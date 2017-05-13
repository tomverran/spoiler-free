package io.tvc.spoilerfree.reddit

import java.security.SecureRandom

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.either._
import cats.syntax.cartesian._
import io.circe.Decoder
import io.circe.parser.decode
import io.tvc.spoilerfree.reddit.AuthError._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class ApiClient(implicit val as: ActorSystem, mat: ActorMaterializer) {
  implicit val ec: ExecutionContext = mat.executionContext

  private val userAgent: `User-Agent` = `User-Agent`("spoiler-free/0.1")
  private val authorize: Uri = "https://www.reddit.com/api/v1/authorize"
  private val accessToken: Uri = "https://www.reddit.com/api/v1/access_token"
  private val subscribe: Uri = "https://oauth.reddit.com/api/subscribe"

  private val http = Http()

  private[reddit] implicit val responseReader: Decoder[AccessTokenResponse] = Decoder.instance { c =>
    (
      c.get[String]("access_token").map(AccessToken) |@|
      c.get[Long]("expires_in") |@|
      c.get[String]("refresh_token").map(RefreshToken)
    ).map(AccessTokenResponse)
  }

  private[reddit] implicit val refreshResponseReader: Decoder[AccessToken] = Decoder.instance { c =>
    c.get[String]("access_token").map(AccessToken)
  }

  private [reddit] val usernameReader: Decoder[String] = Decoder.instance { c =>
    c.get[String]("name")
  }

  /**
    * Get a an OAuth2 authorisation request to send the user to Reddit with
    */
  def authRedirect(config: AuthConfig)(implicit random: SecureRandom): HttpResponse = {
    val state = random.nextLong.toString

    HttpResponse(
      status = StatusCodes.TemporaryRedirect,
      headers = List(
        Location(
          authorize.withQuery(
            Query(
              "state" -> state,
              "client_id" -> config.clientId.value,
              "redirect_uri" -> config.redirect.toString,
              "duration" -> "permanent",
              "scope" -> "subscribe",
              "response_type" -> "code"
            )
          )
        ),
        `Set-Cookie`(HttpCookie("state", state, path = Some("/")))
      )
    )
  }

  /**
    * Verify Reddit's response from the auth request generated above
    * This method will accumulate all errors
    */
  def verifyRedirectResponse(request: HttpRequest): ValidatedNel[AuthError, AuthCode] = {
    val state = request.cookies.find(_.name == "state").fold("")(_.value)
    val query = request.uri.query()
    (
      Validated.fromOption[Unit, RedditNel](query.get("error").map(errorFromString).map(NonEmptyList.of(_)), Unit).swap |@|
      Validated.fromOption[RedditNel, String](query.get("state"), NonEmptyList.of(MissingState)).ensure[RedditNel](NonEmptyList.of(BadState))(_ == state) |@|
      Validated.fromOption[RedditNel, AuthCode](query.get("code").map(AuthCode), NonEmptyList.of(MissingCode))
    ).map { case (_, _, code) => code }
  }


  /**
    * Get an access token from Reddit
    */
  def accessToken(config: AuthConfig, code: AuthCode): Future[Either[NonEmptyList[AuthError], AccessTokenResponse]] =
    http.singleRequest(tokenRequest(config, code)).flatMap(parseJson[AccessTokenResponse])
      .recover { case e: Throwable => Left(NonEmptyList.of(UnknownError(e.getMessage))) }


  /**
    * Get an access token from Reddit
    */
  def accessToken(config: AuthConfig, token: RefreshToken): Future[Either[NonEmptyList[AuthError], AccessToken]] =
    http.singleRequest(refreshTokenRequest(config, token)).flatMap(parseJson[AccessToken])
      .recover { case e: Throwable => Left(NonEmptyList.of(UnknownError(e.getMessage))) }

  /**
    * Generate an http request to make to Reddit to swap the auth code for an access token
    */
  private[reddit] def tokenRequest(config: AuthConfig, code: AuthCode): HttpRequest =
    HttpRequest(POST, accessToken)
      .withEntity(FormData(Query(
        "grant_type" -> "authorization_code",
        "redirect_uri" -> config.redirect.toString,
        "code" -> code.code
      )).toEntity)
      .withHeaders(
        userAgent,
        Authorization(
          BasicHttpCredentials(
            username = config.clientId.value,
            password = config.clientSecret.value
          )
        )
      )

  /**
    * Generate an http request to make to Reddit to swap the auth code for an access token
    */
  private[reddit] def refreshTokenRequest(config: AuthConfig, token: RefreshToken): HttpRequest =
    HttpRequest(POST, accessToken)
      .withEntity(FormData(Query(
        "grant_type" -> "refresh_token",
        "refresh_token" -> token.value
      )).toEntity)
      .withHeaders(
        userAgent,
        Authorization(
          BasicHttpCredentials(
            username = config.clientId.value,
            password = config.clientSecret.value
          )
        )
      )

  /**
    * Generate an http request to make to Reddit to sub or unsub
    */
  private[reddit] def subscribeRequest(token: AccessToken, action: SubscribeAction): HttpRequest =
    HttpRequest(POST, subscribe)
      .withEntity(FormData(Query(
        "action" -> action.value,
        "sr_name" -> "formula1"
      )).toEntity)
      .withHeaders(
        userAgent,
        Authorization(
          OAuth2BearerToken(
            token = token.value
          )
        )
      )

  def subscribe(token: AccessToken, action: SubscribeAction): Future[Either[AuthError, Unit]] =
    for {
      response <- http.singleRequest(subscribeRequest(token, action))
      entity <- response.entity.toStrict(1.second)
    } yield response.status match {
        case e if e.isFailure => Left(UnknownError(entity.data.decodeString("UTF-8")))
        case _ => Right(())
    }

  /**
    * Parse the HTTP response back from reddit
    */
  private[reddit] def parseJson[A](response: HttpResponse)(implicit r: Decoder[A]): Future[Either[NonEmptyList[AuthError], A]] =
    response.entity.toStrict(1.second).map { body =>
      decode[A](body.data.decodeString("UTF-8")).left.map[NonEmptyList[AuthError]] { e =>
        NonEmptyList.of(UnknownError(s"${e.getMessage} - ${body.data.decodeString("UTF-8")}"))
      }
    }

  private[reddit] def errorFromString(in: String): AuthError = in match {
    case "access_denied" => AccessDenied
    case "unsupported_response_type" => UnsupportedResponseType
    case "invalid_scope" => InvalidScope
    case "invalid_request" => InvalidRequest
    case x => UnknownError(x)
  }
}
