package io.tvc.spoilerfree

import java.time.ZonedDateTime


package object racecalendar {
  case class Event(start: ZonedDateTime, end: ZonedDateTime, name: String)
}
