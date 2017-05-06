package io.tvc.spoilerfree.racecalendar

import java.time.{Clock, LocalDateTime, ZoneId, ZonedDateTime}

import akka.stream.scaladsl.{Sink, Source}
import io.tvc.spoilerfree.AkkaContext
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

class RaceCalendarTest extends FreeSpec with Matchers with ScalaFutures with AkkaContext {
  val utc: ZoneId = ZoneId.of("UTC")

  "Race calendar should" - {

    "parse my dubious date with timezone format into a race weekend" in {

      // if the race is on the third of Jan, we expect the weekend to go from two days before

      val expectedWeekend = RaceDates(
        start = LocalDateTime.parse("2017-01-01T00:00:00").atZone(ZoneId.of("Europe/London")),
        end = LocalDateTime.parse("2017-01-03T23:59:59").atZone(ZoneId.of("Europe/London"))
      )

      RaceCalendar.parseWeekend("2017-01-03 12:00:00 Europe/London") shouldEqual expectedWeekend
    }

    "return NormalDay for a day not during a race weekend" in {
      val clock = Clock.fixed(ZonedDateTime.parse("2017-05-02T00:00:00Z").toInstant, utc)
      RaceCalendar.status(clock) shouldEqual NormalDay
    }

    "return RaceWeekend for a day during a race weekend" - {

      "Friday practice sessions" in {
        val clock = Clock.fixed(ZonedDateTime.parse("2017-07-14T07:00:00Z").toInstant, utc)
        RaceCalendar.status(clock) shouldEqual RaceWeekend
      }

      "Saturday qualifying sessions" in {
        val clock = Clock.fixed(ZonedDateTime.parse("2017-07-15T12:00:00Z").toInstant, utc)
        RaceCalendar.status(clock) shouldEqual RaceWeekend
      }

      "The rest of the day after the race" in {
        val clock = Clock.fixed(ZonedDateTime.parse("2017-07-16T21:59:00Z").toInstant, utc)
        RaceCalendar.status(clock) shouldEqual RaceWeekend
      }
    }

    "Only emit changes that occurred in race weekend status" in {
      val statuses = Source(List(NormalDay, NormalDay, NormalDay, RaceWeekend, RaceWeekend, RaceWeekend, NormalDay))
        .via(RaceCalendar.sliding)
        .runWith(Sink.seq)

      whenReady(statuses) { s =>
        s shouldEqual Vector(NormalDay -> RaceWeekend, RaceWeekend -> NormalDay)
      }
    }
  }
}
