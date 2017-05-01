package io.tvc.spoilerfree

import java.time.Clock

import akka.{Done, NotUsed}
import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.SubscribeAction.{Subscribe, Unsubscribe}
import io.tvc.spoilerfree.reddit.{AccessToken, ApiClient, SubscribeAction, TokenStore}
import racecalendar.{NormalDay, RaceCalendar, RaceWeekend}

import scala.concurrent.Future

class Unsubscriber(ts: TokenStore, client: ApiClient) extends LazyLogging {

  case class Result(error: String)

  private def accessTokens: Source[AccessToken, NotUsed] =
    Source.fromFuture(ts.all)
      .mapConcat(identity)
      .mapAsync(parallelism = 1)(r => client.accessToken(settings.authConfig, r))
      .mapConcat {
        case Left(e) =>
          logger.error(e.toString)
          List.empty
        case Right(r) =>
          List(r)
      }

  def run(implicit clock: Clock, mat: Materializer) =
    RaceCalendar.stream.flatMapConcat {
      case (RaceWeekend, NormalDay) => accessTokens.mapAsync(1)(client.subscribe(_, Subscribe)).map(Result)
      case (NormalDay, RaceWeekend) => accessTokens.mapAsync(1)(client.subscribe(_, Unsubscribe)).map(Result)
    }.runWith(Sink.foreach(r => logger.info(r.error)))

  def manual(action: SubscribeAction)(implicit mat: Materializer): Future[String] =
    accessTokens.mapAsync(1)(client.subscribe(_, action)).map(Result)
    .runWith(Sink.fold("") { case(a, b) => a + b })
}
