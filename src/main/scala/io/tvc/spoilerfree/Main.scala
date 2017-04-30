package io.tvc.spoilerfree

import java.security.SecureRandom

import akka.actor.ActorSystem
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.data.{EitherT, NonEmptyList}
import cats.instances.all._
import cats.syntax.all._
import scala.concurrent.Future
import scala.language.higherKinds

object Main extends App {

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext
  implicit val random = new SecureRandom()

  val client: reddit.ApiClient = new reddit.ApiClient
  type ET[F[_], A] = EitherT[F, NonEmptyList[reddit.AuthError], A]


  val index = pathEndOrSingleSlash {
    getFromResource("http/index.html")
  }

  val authorise = path("authorise") {
    complete(client.authRedirect(Settings.authConfig))
  }

  val redirect = path("redirect") { ctx =>
    val result = for {
      authCode <- EitherT(client.verifyRedirectResponse(ctx.request).toEither.pure[Future])
      response <- EitherT(client.accessToken(Settings.authConfig, authCode).map(_.toEither))
      user <- EitherT(client.username(Settings.authConfig, response.token).map(_.toEither))
    } yield user
    ctx.complete(result.valueOr(err => err.toList.mkString))
  }

  Http().bindAndHandle(index ~ authorise ~ redirect, "0.0.0.0", port = 8080)
}
