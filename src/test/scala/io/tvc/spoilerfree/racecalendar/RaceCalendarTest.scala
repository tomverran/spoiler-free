package io.tvc.spoilerfree.racecalendar

import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.HttpEntity
import io.tvc.spoilerfree.AkkaContext
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.io.Source

class RaceCalendarTest extends FreeSpec with Matchers with ScalaFutures with AkkaContext {
  override implicit val patienceConfig = PatienceConfig(5.seconds, 100.millis)
  val utc: ZoneId = ZoneId.of("UTC")

  "Race calendar should" - {
    val calendar = Source.fromURL(getClass.getResource("/f1-calendar_gp.ics")).mkString
    val parsed = RaceCalendar.parseEvents(HttpEntity(calendar))

    "Produce a list of ical events from an HttpResponse" in {
      whenReady(parsed) { events =>
        val expectedRaces = Set("United States Grand Prix", "Mexican Grand Prix", "Brazilian Grand Prix")
        events.right.get.map(_.name).toSet shouldEqual expectedRaces
      }
    }

    "Set the start time of the event back two days and the end time forward one day" in {
      whenReady(parsed) { events =>
        val us = events.right.get.find(_.name == "United States Grand Prix").get
        us.start shouldEqual ZonedDateTime.parse("2017-10-20T19:00:00Z")
        us.end shouldEqual ZonedDateTime.parse("2017-10-23T21:00:00Z")
      }
    }
  }
}
