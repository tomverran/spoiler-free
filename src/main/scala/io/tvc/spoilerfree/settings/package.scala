package io.tvc.spoilerfree

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter._

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

package object settings {
  private val config = ConfigFactory.load

  lazy val clientId = reddit.ClientId(config.getString("reddit.client-id"))
  lazy val clientSecret = reddit.ClientSecret(config.getString("reddit.client-secret"))
  lazy val authConfig = reddit.AuthConfig(clientId, clientSecret, "http://localhost:8080/redirect")
  lazy val dynamoTable = config.getString("aws.dynamo-table")

  lazy val tvTimes = config.getStringList("f1.races").asScala.toList
}
