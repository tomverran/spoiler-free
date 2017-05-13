package io.tvc.spoilerfree

import java.security.SecureRandom
import java.time.{Clock, ZonedDateTime, Duration => JDuration}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import cats.data.{EitherT, NonEmptyList}
import cats.instances.all._
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.racecalendar.RaceCalendar
import io.tvc.spoilerfree.reddit.TokenStore.TokenStoreError
import shapeless.ops.coproduct.Inject
import shapeless.{:+:, CNil}

import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

object Main extends App with LazyLogging {

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer(
    ActorMaterializerSettings(as).withSupervisionStrategy { e =>
      logger.error(e.getMessage, e.getCause)
      Supervision.Resume
    }
  )
  implicit val ec = mat.executionContext
  implicit val random = new SecureRandom()
  implicit val clock = Clock.systemUTC

  type Error = TokenStoreError :+: reddit.AuthError :+: CNil
  type ET[F[_], A] = EitherT[F, NonEmptyList[Error], A]

  implicit class ETOps[A, B](a: EitherT[Future, NonEmptyList[B], A])(implicit inject: Inject[Error, B]) {
    def align: EitherT[Future, NonEmptyList[Error], A] = a.leftMap(_.map(inject.apply))
  }

  val tokens: reddit.TokenStore = new reddit.TokenStore
  val client: reddit.ApiClient = new reddit.ApiClient
  val scheduler = new Scheduler(tokens, client)

  val index = pathEndOrSingleSlash {
    getFromResource("http/index.html")
  }

  val styles = path("styles.css") {
    getFromResource("http/styles.css")
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
      authCode <- EitherT(client.verifyRedirectResponse(ctx.request).toEither.pure[Future]).align
      response <- EitherT(client.accessToken(settings.authConfig, authCode)).align
      _ <- EitherT(tokens.put(response.refresh)).leftMap(NonEmptyList.of(_)).align
      result <- getFromResource("http/thanks.html").apply(ctx).liftT[ET]
    } yield result

    result.leftMap { errs =>
      errs.toList.foreach(s => logger.error(s.toString))
    }.getOrElseF(getFromResource("http/error.html").apply(ctx))
  }

  val schedules = scheduler.schedule(RaceCalendar.dates, now = ZonedDateTime.now)
  Http().bindAndHandle(index ~ styles ~ authorise ~ redirect, "0.0.0.0", port = 8080).recover {
    case _: Throwable =>
      schedules.foreach(_.cancel)
      logger.info(s"Cancelled ${schedules.length} tasks")
      as.terminate.foreach(_ => sys.exit(1))
  }
}
