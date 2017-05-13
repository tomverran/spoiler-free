package io.tvc.spoilerfree

import java.lang.Math._
import java.time.{Duration, ZonedDateTime}

import akka.Done
import akka.actor.{ActorSystem, Cancellable}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.racecalendar.RaceDates
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}
import io.tvc.spoilerfree.reddit._
import cats.instances.future._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class Scheduler(ts: TokenStore, client: ApiClient)(implicit ec: ExecutionContext, mat: ActorMaterializer) extends LazyLogging {

  private def tokenOrRemove(r: RefreshToken): Future[Option[AccessToken]] =
    EitherT(client.accessToken(settings.authConfig, r))
      .map(Some(_))
      .leftMap(er => logger.error(er.toString))
      .getOrElseF {
        logger.debug("Removing invalid token")
        ts.delete(r).map(_ => None)
      }

  private def run(action: SubscribeAction)(implicit mat: Materializer): Future[Done] = {
    logger.debug(s"Running $action")
    Source.fromFuture(ts.all)
      .mapConcat(identity)
      .mapAsync(parallelism = 1)(tokenOrRemove)
      .mapConcat(_.toList)
      .mapAsync(parallelism = 1)(client.subscribe(_, action))
      .runWith(Sink.foreach[String](logger.error(_)))
  }

  def schedule(dates: List[RaceDates], now: ZonedDateTime)(implicit as: ActorSystem): List[Cancellable] =
    dates.filter(_.end.isAfter(now))
      .flatMap(r => List(r.start -> Unsubscribe, r.end -> Subscribe))
      .map { case (time, action) =>
        logger.debug(s"Scheduling $action at $time")
        val seconds = max(Duration.between(now, time).getSeconds, 0)
        as.scheduler.scheduleOnce(seconds.seconds)(run(action))
      }
}
