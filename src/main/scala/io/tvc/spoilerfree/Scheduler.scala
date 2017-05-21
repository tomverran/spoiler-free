package io.tvc.spoilerfree

import java.lang.Math._
import java.time.{Duration, ZonedDateTime}

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import cats.instances.future._
import cats.instances.unit._
import cats.syntax.cartesian._
import cats.syntax.either._
import cats.syntax.semigroup._
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.racecalendar.Event
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}
import io.tvc.spoilerfree.reddit.TokenStore.TokenStoreError
import io.tvc.spoilerfree.reddit._
import net.fortuna.ical4j.util.CompatibilityHints._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class Scheduler(ts: TokenStore, client: ApiClient)(implicit ec: ExecutionContext, mat: ActorMaterializer) extends LazyLogging {

  // scary ical global properties
  setHintEnabled(KEY_RELAXED_VALIDATION, true)
  setHintEnabled(KEY_RELAXED_UNFOLDING, true)
  setHintEnabled(KEY_RELAXED_PARSING, true)

  /**
    * Akka streams uses exceptions for errors
    * but I can't quite bring myself to do that globally
    */
  def unsafe[E, A]: Flow[Either[E, A], A, NotUsed] =
    Flow[Either[E, A]].map(_.fold(e => throw new Exception(e.toString), identity))

  /**
    * Grab all the valid refresh tokens from Dynamo and convert them into access tokens,
    * removing any refresh tokens which are now invalid
    */
  private def tokenOrRemove(u: UserDetails[RefreshToken]): Future[Either[TokenStoreError, Option[UserDetails[AccessToken]]]] =
    client.accessToken(settings.authConfig, u.token).flatMap {
      case Right(token) =>
        Future.successful(Right(Some(u.copy(token = token))))
      case Left(errs) =>
        logger.debug(s"Removing invalid token due to $errs")
        ts.delete(u).map(_.map(_ => None))
    }

  /**
    *  Apply the given action to all current subscribers
    *  i.e. unsubscribe or subscribe everyone from or to the subreddit
    */
  private def run(action: SubscribeAction): Future[Done] = {
    logger.debug(s"Running $action")
    Source.fromFuture(ts.all)
      .mapConcat(identity)
      .delay(1.seconds)
      .mapAsync(parallelism = 1)(tokenOrRemove)
      .via(unsafe)
      .mapConcat(_.toList)
      .mapAsync(parallelism = 1) { user =>
        (client.subscribe(user.token, action) |@| client.filterRAll(user, action)).tupled.map { case (a, b) =>
          (a.toValidated |+| b.toValidated).toEither
        }
      }
      .via(unsafe)
      .runWith(Sink.ignore)
  }

  /**
    * Given a list of race dates, schedule in the respective unsubscribe and subscribe tasks
    */
  def schedule(events: List[Event], now: ZonedDateTime)(implicit as: ActorSystem): List[Cancellable] =
    events.filter(_.end.isAfter(now))
      .flatMap(r => List((r.name, r.start, Unsubscribe), (r.name, r.end, Subscribe)))
      .map { case (name, time, action) =>
        logger.debug(s"Scheduling $action at $time - $name")
        val seconds = max(Duration.between(now, time).getSeconds, 0)
        as.scheduler.scheduleOnce(seconds.seconds)(run(action))
      }
}
