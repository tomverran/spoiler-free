package io.tvc.spoilerfree

import java.lang.Math._
import java.time.{Duration, ZonedDateTime}

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.racecalendar.RaceDates
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}
import io.tvc.spoilerfree.reddit.TokenStore.TokenStoreError
import io.tvc.spoilerfree.reddit._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class Scheduler(ts: TokenStore, client: ApiClient)(implicit ec: ExecutionContext, mat: ActorMaterializer) extends LazyLogging {

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
  private def tokenOrRemove(r: RefreshToken): Future[Either[TokenStoreError, Option[AccessToken]]] =
    client.accessToken(settings.authConfig, r).flatMap {
      case Right(token) => Future.successful(Right(Some(token)))
      case Left(errs) =>
        logger.debug(s"Removing invalid token due to $errs")
        ts.delete(r).map(_.map(_ => None))
    }

  /**
    *  Apply the given action to all current subscribers
    *  i.e. unsubscribe or subscribe everyone from or to the subreddit
    */
  private def run(action: SubscribeAction)(implicit mat: Materializer): Future[Done] = {
    logger.debug(s"Running $action")
    Source.fromFuture(ts.all)
      .mapConcat(identity)
      .mapAsync(parallelism = 1)(tokenOrRemove)
      .via(unsafe)
      .mapConcat(_.toList)
      .mapAsync(parallelism = 1)(client.subscribe(_, action))
      .via(unsafe)
      .runWith(Sink.ignore)
  }

  /**
    * Given a list of race dates, schedule in the respective unsubscribe and subscribe tasks
    */
  def schedule(dates: List[RaceDates], now: ZonedDateTime)(implicit as: ActorSystem): List[Cancellable] =
    dates.filter(_.end.isAfter(now))
      .flatMap(r => List(r.start -> Unsubscribe, r.end -> Subscribe))
      .map { case (time, action) =>
        logger.debug(s"Scheduling $action at $time")
        val seconds = max(Duration.between(now, time).getSeconds, 0)
        as.scheduler.scheduleOnce(seconds.seconds)(run(action))
      }
}
