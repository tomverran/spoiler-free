package io.tvc.spoilerfree

import akka.http.scaladsl.model.Uri
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

package object settings {
  private val config = ConfigFactory.load

  private lazy val clientId = reddit.ClientId(config.getString("reddit.client-id"))
  private lazy val clientSecret = reddit.ClientSecret(config.getString("reddit.client-secret"))
  private lazy val redirectUrl: Uri = config.getString("reddit.redirect-url")

  lazy val authConfig = reddit.AuthConfig(clientId, clientSecret, redirectUrl)
  lazy val dynamoTable = config.getString("aws.dynamo-table")
  lazy val raceDates = config.getStringList("f1.races").asScala.toList
}
