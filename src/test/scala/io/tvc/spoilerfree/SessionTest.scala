package io.tvc.spoilerfree

import java.util.Base64

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class SessionTest extends FreeSpec with Matchers with GeneratorDrivenPropertyChecks {

  "Session object" - {

    "Should be able to serialise and deserialise an arbitrary string" in {
      forAll { user: String =>
        Session.check(Session.create(user)) shouldEqual Right(user)
      }
    }

    "Should not allow the message to be tampered with" in {
      forAll { (user: String, byte: Byte, index: Int) =>

        val cookie = Session.create(user)
        val signedText = Base64.getDecoder.decode(cookie.value.toString)
        val indexToChange = Math.abs(index % signedText.length - 1)

        whenever(signedText(indexToChange) != byte) {
          val tamperedWith = Base64.getEncoder.encode(signedText.updated(indexToChange, byte))
          Session.check(cookie.copy(value = new String(tamperedWith))) shouldEqual Left("failure")
        }
      }
    }
  }
}
