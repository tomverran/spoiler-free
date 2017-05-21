package io.tvc.spoilerfree

import akka.http.scaladsl.model.Uri
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

package object settings {
  private val config = ConfigFactory.load

  lazy val appSecret = config.getString("app.secret").getBytes.ensuring(_.length == 64, s"incorrect length")
  private lazy val clientId = reddit.ClientId(config.getString("reddit.client-id"))
  private lazy val clientSecret = reddit.ClientSecret(config.getString("reddit.client-secret"))
  private lazy val redirectUrl: Uri = config.getString("reddit.redirect-url")
  lazy val httpPort: Int = config.getInt("http.port")

  lazy val authConfig = reddit.AuthConfig(clientId, clientSecret, redirectUrl)
  lazy val dynamoTable = config.getString("aws.dynamo-table")
  lazy val icalUrl = config.getString("subreddits.formula1"): Uri
}
