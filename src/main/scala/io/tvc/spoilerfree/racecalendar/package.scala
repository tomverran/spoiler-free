package io.tvc.spoilerfree

import java.time.ZonedDateTime


package object racecalendar {

  sealed trait CalendarStatus
  case object NormalDay extends CalendarStatus
  case object RaceWeekend extends CalendarStatus
  case class RaceDates(start: ZonedDateTime, end: ZonedDateTime)
}
