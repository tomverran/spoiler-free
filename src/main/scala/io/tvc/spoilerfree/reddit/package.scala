package io.tvc.spoilerfree

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.HttpCookie
import cats.data.NonEmptyList


package object reddit {

  sealed trait RedditError
  object RedditError {

    // these come from Reddit itself
    case object AccessDenied extends RedditError
    case object UnsupportedResponseType extends RedditError
    case object InvalidScope extends RedditError
    case object InvalidRequest extends RedditError

    // these are parse issues
    case object BadState extends RedditError
    case object MissingCode extends RedditError
    case object MissingState extends RedditError
    case class UnexpectedJson(data: String) extends RedditError

    //good grief who knows what this might be
    case class UnknownError(what: String) extends RedditError
  }

  sealed abstract class SubscribeAction(val value: String)

  case object SubscribeAction {
    case object Subscribe extends SubscribeAction(value = "sub")
    case object Unsubscribe extends SubscribeAction(value = "unsub")
  }

  type State = String
  type RedditNel = NonEmptyList[RedditError]

  case class ClientId(value: String)
  case class ClientSecret(value: String)
  case class AuthConfig(clientId: ClientId, clientSecret: ClientSecret, redirect: Uri)

  case class AccessToken(value: String)
  case class RefreshToken(value: String)
  case class AccessTokenResponse(token: AccessToken, expires: Long, refresh: RefreshToken)
  case class UserDetails[A](name: String, token: A)

  case class AuthRedirect(uri: Uri, state: HttpCookie)
  case class AuthCode(code: String)
}
