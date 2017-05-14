package io.tvc.spoilerfree.reddit

import java.net.URLEncoder
import java.security.SecureRandom

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.either._
import cats.syntax.cartesian._
import io.circe.{Decoder, Json}
import io.circe.parser.decode
import io.tvc.spoilerfree.reddit.RedditError._
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class ApiClient(implicit val as: ActorSystem, mat: ActorMaterializer) {
  implicit val ec: ExecutionContext = mat.executionContext

  private[reddit] val userAgent: `User-Agent` = `User-Agent`("spoiler-free/0.1 (by /u/Javaguychronox)")
  private[reddit] val authorize: Uri = "https://www.reddit.com/api/v1/authorize"
  private[reddit] val accessToken: Uri = "https://www.reddit.com/api/v1/access_token"
  private[reddit] val subscribe: Uri = "https://oauth.reddit.com/api/subscribe"
  private[reddit] val me: Uri = "https://oauth.reddit.com/api/v1/me"

  private val http = Http()

  private[reddit] implicit val accessTokenReader: Decoder[AccessTokenResponse] = Decoder.instance { c =>
    (
      c.get[String]("access_token").map(AccessToken) |@|
      c.get[Long]("expires_in") |@|
      c.get[String]("refresh_token").map(RefreshToken)
    ).map(AccessTokenResponse)
  }

  private[reddit] implicit val refreshTokenReader: Decoder[AccessToken] = Decoder.instance { c =>
    c.get[String]("access_token").map(AccessToken)
  }

  private [reddit] val identityReader: Decoder[String] = Decoder.instance { c =>
    c.get[String]("name")
  }

  private[reddit] val errorReader: Decoder[RedditError] = Decoder.instance { c =>
    c.get[String]("error").map(errorFromString)
  }

  private[reddit] val unitReader: Decoder[Unit] = Decoder.instance { _ =>
    Right(())
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
              "scope" -> "subscribe identity",
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
  def verifyRedirectResponse(request: HttpRequest): ValidatedNel[RedditError, AuthCode] = {
    val state = request.cookies.find(_.name == "state").fold("")(_.value)
    val query = request.uri.query()
    (
      Validated.fromOption[Unit, RedditNel](query.get("error").map(errorFromString).map(NonEmptyList.of(_)), Unit).swap |@|
      Validated.fromOption[RedditNel, String](query.get("state"), NonEmptyList.of(MissingState)).ensure[RedditNel](NonEmptyList.of(BadState))(_ == state) |@|
      Validated.fromOption[RedditNel, AuthCode](query.get("code").map(AuthCode), NonEmptyList.of(MissingCode))
    ).map { case (_, _, code) => code }
  }


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
    * Generate an http request to make to Reddit to swap the auth code for an access token
    */
  private[reddit] def identityRequest(token: AccessToken): HttpRequest =
    HttpRequest(GET, me)
      .withHeaders(
        userAgent,
        Authorization(
          OAuth2BearerToken(
            token = token.value
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

  private[reddit] def filterRequest(user: UserDetails[AccessToken], action: SubscribeAction) = {
    val verb = action match {
      case Subscribe => DELETE // because you're removing the filter to subscribe
      case Unsubscribe => PUT
    }

    val name = URLEncoder.encode(user.name, "UTF-8")
    val url: Uri = s"https://oauth.reddit.com/api/filter/user/$name/f/all/r/formula1"

    HttpRequest(verb, url)
      .withHeaders(
        Authorization(OAuth2BearerToken(user.token.value)),
        userAgent
      )
      .withEntity(
        FormData(
          Query(
            "model" -> Json.obj("name" -> Json.fromString("formula1")).noSpaces
          )
        ).toEntity
      )
  }

  def accessToken(config: AuthConfig, code: AuthCode): Future[Either[NonEmptyList[RedditError], AccessTokenResponse]] =
    runRequest(tokenRequest(config, code), accessTokenReader)

  def accessToken(config: AuthConfig, token: RefreshToken): Future[Either[NonEmptyList[RedditError], AccessToken]] =
    runRequest(refreshTokenRequest(config, token), refreshTokenReader)

  def subscribe(token: AccessToken, action: SubscribeAction): Future[Either[NonEmptyList[RedditError], Unit]] =
    runRequest(subscribeRequest(token, action), unitReader)

  def filterRAll(user: UserDetails[AccessToken], action: SubscribeAction) =
    runRequest(filterRequest(user, action), unitReader)

  def identity(token: AccessToken): Future[Either[NonEmptyList[RedditError], String]] =
    runRequest(identityRequest(token), identityReader)

  /**
    * Parse the HTTP response back from reddit
    */
  private[reddit] def runRequest[A](request: HttpRequest, d: Decoder[A]): Future[Either[NonEmptyList[RedditError], A]] = (
    for {
      response <- http.singleRequest(request)
      body <- response.entity.toStrict(1.second)
      data = body.data.decodeString("UTF-8")
      json = if (data.nonEmpty) data else "{}"
    } yield {
      response.status match {
        case a if a.isSuccess => decode[A](json)(d).left.map(_ => UnexpectedJson(data))
        case _ => Left(decode[RedditError](data)(errorReader).getOrElse(UnknownError(data)))
      }
    }
  ).recover {
    case e: Throwable => Left(UnknownError(e.getMessage))
  }.map(_.left.map(NonEmptyList.of(_)))

  private[reddit] def errorFromString(in: String): RedditError = in match {
    case "access_denied" => AccessDenied
    case "unsupported_response_type" => UnsupportedResponseType
    case "invalid_scope" => InvalidScope
    case "invalid_request" => InvalidRequest
    case x => UnknownError(x)
  }
}
