package io.tvc.spoilerfree.racecalendar

import java.time.ZoneId
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import cats.syntax.either._
import io.tvc.spoilerfree.settings
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object RaceCalendar {

  type CalendarError = Throwable
  private val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor)

  def events(
    implicit
    as: ActorSystem,
    mat: ActorMaterializer,
    ec: ExecutionContext
  ): Future[Either[CalendarError, List[Event]]] =
    for {
    httpResponse <- Http().singleRequest(HttpRequest(uri = settings.icalUrl))
    httpEntity <- httpResponse.entity.toStrict(10.seconds)
    dates <- parseEvents(httpEntity)
  } yield dates

  /**
    * Grab list of sane events from ical4j
    */
  private[racecalendar] def parseEvents(
    e: HttpEntity
  )(
    implicit
    mat: ActorMaterializer
  ): Future[Either[CalendarError, List[Event]]] =
    Future {
      Either.catchNonFatal {
        val cal = new CalendarBuilder().build(e.dataBytes.runWith(StreamConverters.asInputStream(2.seconds)))
        cal.getComponents.asScala.flatMap(c => Try(c.asInstanceOf[VEvent]).toOption).toList.map { event =>
          val zone = Option(event.getStartDate.getTimeZone).fold(ZoneId.of("Z"))(_.toZoneId)
          Event(
            event.getStartDate.getDate.toInstant.atZone(zone).minusDays(2),
            event.getEndDate.getDate.toInstant.atZone(zone).plusDays(1),
            Option(event.getSummary).map(_.getValue).mkString
          )
        }
      }
    }(ec)
}
