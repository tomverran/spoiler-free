package io.tvc.spoilerfree.racecalendar

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.tvc.spoilerfree.settings

object RaceCalendar {

  private[racecalendar] def parseWeekend(date: String): RaceDates = {
    val raceStart = ZonedDateTime.parse(date, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss VV"))
    RaceDates(start = raceStart.minusDays(2), raceStart.plusDays(1))
  }

  val dates = settings.raceDates.map(parseWeekend)
}
