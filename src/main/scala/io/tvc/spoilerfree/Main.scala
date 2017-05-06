package io.tvc.spoilerfree

import java.security.SecureRandom
import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.data.{EitherT, NonEmptyList}
import cats.instances.all._
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}

import scala.concurrent.Future
import scala.language.higherKinds

object Main extends App with LazyLogging {

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext
  implicit val random = new SecureRandom()
  implicit val clock = Clock.systemUTC

  type ET[F[_], A] = EitherT[F, NonEmptyList[reddit.AuthError], A]

  val tokens: reddit.TokenStore = new reddit.TokenStore
  val client: reddit.ApiClient = new reddit.ApiClient
  val unsubscriber = new Unsubscriber(tokens, client)

  val index = pathEndOrSingleSlash {
    getFromResource("http/index.html")
  }

  /**
    * Redirect to reddit and set a state cookie
    */
  val authorise = path("authorise") {
    complete(client.authRedirect(settings.authConfig))
  }

  /**
    * Place a refresh token from Reddit into DynamoDb
    * allowing it to be used to subscribe / unsubscribe later
    */
  val redirect = path("redirect") { ctx =>
    val result: ET[Future, RouteResult] = for {
      authCode <- EitherT(client.verifyRedirectResponse(ctx.request).toEither.pure[Future])
      response <- EitherT(client.accessToken(settings.authConfig, authCode))
      _ <- EitherT(tokens.put(response.refresh)).leftMap(NonEmptyList.of(_))
      result <- getFromResource("http/thanks.html").apply(ctx).liftT[ET]
    } yield result

    result.leftMap { errs =>
      errs.toList.foreach(s => logger.error(s.toString))
    }.getOrElseF(getFromResource("http/error.html").apply(ctx))
  }

  // start the actual http server
  Http().bindAndHandle(index ~ authorise ~ redirect, "0.0.0.0", port = 8080)
  unsubscriber.run
}
