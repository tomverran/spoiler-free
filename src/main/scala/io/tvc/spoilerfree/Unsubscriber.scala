package io.tvc.spoilerfree

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.{AccessToken, ApiClient, SubscribeAction, TokenStore}

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

  def run(action: SubscribeAction)(implicit mat: Materializer): Future[Done] = {
    logger.info(s"Running $action")
    accessTokens.mapAsync(1)(client.subscribe(_, action)).map(Result)
    .runWith(Sink.foreach[Result](e => logger.info(e.error)))
  }
}
