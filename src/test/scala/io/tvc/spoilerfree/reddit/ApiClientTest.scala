package io.tvc.spoilerfree.reddit

import java.security.SecureRandom

import akka.http.scaladsl.model.{HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.model.Uri.Query
import cats.data.{NonEmptyList, Validated}
import io.tvc.spoilerfree.reddit.AuthError._
import org.scalatest.{FreeSpec, Matchers}
import akka.http.scaladsl.model.MediaTypes.`application/x-www-form-urlencoded`
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.headers._
import io.tvc.spoilerfree.AkkaContext


class ApiClientTest extends FreeSpec with Matchers with AkkaContext {
  val client = new ApiClient()

  implicit object ConstantRandom extends SecureRandom {
    override def nextLong() = constant
    val constant = 1234345345245l
  }

  val clientId = ClientId("foo")
  val redirectUri = Uri("https://example.com")
  val auth = AuthConfig(clientId, ClientSecret("foo"), redirectUri)
  val authResponse = client.authRedirect(auth)
  val state = ConstantRandom.constant.toString

  "Authorisation URI generator" - {

    val uri = authResponse.headers.collect { case l: Location => l }.head.uri
    val state = authResponse.headers.collect { case `Set-Cookie`(c) => c.value }.head

    "Be prefixed with the reddit authorization endpoint" in {
      uri.authority.host.toString shouldEqual "www.reddit.com"
      uri.scheme.toString shouldEqual "https"
      uri.path.toString shouldEqual "/api/v1/authorize"
    }

    "Pass through the client ID" in {
      uri.query().get("client_id") shouldEqual Some(clientId.value)
    }

    "Set response type to the literal string 'code'" in {
      uri.query().get("response_type") shouldEqual Some("code")
    }

    "Include a state parameter in both the URI and the AuthorisationUri object" in {
      uri.query().get("state") shouldEqual Some(state)
      state shouldEqual ConstantRandom.constant.toString
    }

    "Include the given redirect URI" in {
      uri.query().get("redirect_uri") shouldEqual Some(redirectUri.toString)
    }

    "Set the duration to permanent" in {
      uri.query().get("duration") shouldEqual Some("permanent")
    }

    "Set the scopes to identity and subscribe" in {
      uri.query().get("scope") shouldEqual Some("identity subscribe")
    }
  }

  "Authorization URI validator" - {

    val codeParam = "code" -> "1234"
    val stateParam = "state" -> ConstantRandom.constant.toString

    val accessDenied = "error" -> "access_denied"
    val badResponseType = "error" -> "unsupported_response_type"
    val invalidScope = "error" -> "invalid_scope"
    val invalidRequest = "error" -> "invalid_request"

    def errors(redditError: AuthError*) =
      Validated.invalid(NonEmptyList.of(redditError.head, redditError.tail:_*))

    def req(q: Query) =
      HttpRequest(uri = Uri("foo").withQuery(q)).withHeaders(Cookie("state", state))

    "Return an error if there is an error parameter in the query string" in {

      client.verifyRedirectResponse(req(Query(accessDenied, codeParam, stateParam))) shouldEqual errors(AccessDenied)
      client.verifyRedirectResponse(req(Query(badResponseType, codeParam, stateParam))) shouldEqual errors(UnsupportedResponseType)
      client.verifyRedirectResponse(req(Query(invalidScope, codeParam, stateParam))) shouldEqual errors(InvalidScope)
      client.verifyRedirectResponse(req(Query(invalidRequest, codeParam, stateParam))) shouldEqual errors(InvalidRequest)
      client.verifyRedirectResponse(req(Query("error" -> "dunno", codeParam, stateParam))) shouldEqual errors(UnknownError("dunno"))
    }

    "Return an error if there is no state in the query string" in {
      client.verifyRedirectResponse(req(Query(codeParam))) shouldEqual errors(MissingState)
    }

    "Return an error if the wrong state is in the query string" in {
      client.verifyRedirectResponse(req(Query(codeParam, "state" -> "foo"))) shouldEqual errors(BadState)
    }

    "Return an error if no code is in the query string" in {
      client.verifyRedirectResponse(req(Query(stateParam))) shouldEqual errors(MissingCode)
    }

    "Accumulate errors" in {
      client.verifyRedirectResponse(req(Query(accessDenied))) shouldEqual errors(AccessDenied, MissingState, MissingCode)
    }
  }

  "Generate auth code" - {

    val authCode = AuthCode("foo")
    val secret = ClientSecret("so secret")
    val req = client.tokenRequest(AuthConfig(clientId, secret, redirectUri), authCode)

    implicit class SensibleBody(in: RequestEntity) {
      def contents = Query(in.asInstanceOf[Strict].data.decodeString("UTF-8"))
    }

    "Go to the right reddit endpoint" in {
      req.uri.toString shouldEqual "https://www.reddit.com/api/v1/access_token"
    }

    "Be posting form urlencoded data" in {
      req.entity.contentType.mediaType shouldEqual `application/x-www-form-urlencoded`
    }

    "Contain the right grant type" in {
      req.entity.contents.get("grant_type") shouldEqual Some("authorization_code")
    }

    "Contain the access code" in {
      req.entity.contents.get("code") shouldEqual Some(authCode.code)
    }

    "Contain the redirect uri" in {
      req.entity.contents.get("redirect_uri") shouldEqual Some(redirectUri.toString)
    }
  }
}