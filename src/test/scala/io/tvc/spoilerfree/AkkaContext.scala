package io.tvc.spoilerfree

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterAll, Suite}

trait AkkaContext extends BeforeAndAfterAll { self: Suite =>

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def afterAll() = {
    as.terminate()
  }
}
