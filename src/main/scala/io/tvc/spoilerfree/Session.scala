package io.tvc.spoilerfree

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import akka.http.scaladsl.model.headers.HttpCookie
import akka.util.ByteString

/**
  * Creates & checks a token cookie containing a username and an HMAC,
  * like a dubious homebrew JWT but without the ridiculous client side algorithm selection
  *
  * I'm of course terrified of any kind of home made crypto but since this is basically
  * just a message concatenated with its HMAC I can't see many avenues for incompetence
  */
object Session {
  private val mac = Mac.getInstance("HmacSHA256")
  mac.init( new SecretKeySpec(settings.appSecret, "HmacSHA256"))

  def create(u: String): HttpCookie = {
    val token = ByteString(s"$u\00".getBytes) ++ ByteString(mac.doFinal(u.getBytes("UTF-8")))
    HttpCookie("token", new String(Base64.getEncoder.encode(token.toByteBuffer).array))
  }

  def check(c: HttpCookie): Either[String, String] = {
    val (name, hmac) = Base64.getDecoder.decode(c.value).toList.span(_ != 0)
    if (mac.doFinal(name.toArray).toSeq == hmac.drop(1)) {
      Right[String, String](new String(name.toArray))
    } else {
     Left("failure")
    }
  }
}
