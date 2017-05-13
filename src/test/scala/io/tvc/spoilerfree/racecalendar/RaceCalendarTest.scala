package io.tvc.spoilerfree.racecalendar

import java.time.{LocalDateTime, ZoneId}

import io.tvc.spoilerfree.AkkaContext
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

class RaceCalendarTest extends FreeSpec with Matchers with ScalaFutures with AkkaContext {
  val utc: ZoneId = ZoneId.of("UTC")

  "Race calendar should" - {

    "parse my dubious date with timezone format into a race weekend" in {

      // if the race is on the third of Jan, we expect the weekend to go from two days before to one day after

      val expectedWeekend = RaceDates(
        start = LocalDateTime.parse("2017-01-01T12:00:00").atZone(ZoneId.of("Europe/London")),
        end = LocalDateTime.parse("2017-01-04T12:00:00").atZone(ZoneId.of("Europe/London"))
      )

      RaceCalendar.parseWeekend("2017-01-03 12:00:00 Europe/London") shouldEqual expectedWeekend
    }
  }
}
