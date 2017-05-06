package io.tvc.spoilerfree.racecalendar

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZonedDateTime}

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.{Flow, Source}
import io.tvc.spoilerfree.settings

import scala.concurrent.duration._

object RaceCalendar {

  private[racecalendar] def parseWeekend(date: String): RaceDates = {
    val raceStart = ZonedDateTime.parse(date, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss VV"))
    RaceDates(start = raceStart.minusDays(2).withHour(0), raceStart.withHour(23).withMinute(59).withSecond(59))
  }

  private val dates = settings.tvTimes.map(parseWeekend)

  private[racecalendar] def status(implicit clock: Clock): CalendarStatus = {
    val now = ZonedDateTime.now(clock)
    dates.find(d => d.start.isBefore(now) && d.end.isAfter(now)).map(_ => RaceWeekend)
      .getOrElse(NormalDay)
  }

  private[racecalendar] val sliding: Flow[CalendarStatus, (CalendarStatus, CalendarStatus), NotUsed] =
    Flow[CalendarStatus].sliding(n = 2, step = 1).mapConcat {
      case (Seq(a, b)) if a != b => List(a -> b)
      case _ => List.empty
    }

  def stream(implicit clock: Clock): Source[(CalendarStatus, CalendarStatus), Cancellable] =
    Source.tick(0.seconds, 1.hour, ())
      .map(_ => status)
      .via(sliding)
}
