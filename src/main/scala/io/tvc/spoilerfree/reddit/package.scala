package io.tvc.spoilerfree

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.HttpCookie
import cats.data.NonEmptyList


package object reddit {

  sealed trait AuthError
  object AuthError {

    // these come from Reddit itself
    case object AccessDenied extends AuthError
    case object UnsupportedResponseType extends AuthError
    case object InvalidScope extends AuthError
    case object InvalidRequest extends AuthError

    // these are response URL parse issues
    case object BadState extends AuthError
    case object MissingCode extends AuthError
    case object MissingState extends AuthError

    //good grief who knows what this might be
    case class UnknownError(what: String) extends AuthError
  }

  sealed abstract class SubscribeAction(val value: String)

  case object SubscribeAction {
    case object Subscribe extends SubscribeAction(value = "sub")
    case object Unsubscribe extends SubscribeAction(value = "unsub")
  }

  type State = String
  type RedditNel = NonEmptyList[AuthError]

  case class ClientId(value: String)
  case class ClientSecret(value: String)
  case class AuthConfig(clientId: ClientId, clientSecret: ClientSecret, redirect: Uri)

  case class AccessToken(value: String)
  case class RefreshToken(value: String)
  case class AccessTokenResponse(token: AccessToken, expires: Long, refresh: RefreshToken)

  case class AuthRedirect(uri: Uri, state: HttpCookie)
  case class AuthCode(code: String)
}
