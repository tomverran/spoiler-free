package io.tvc.spoilerfree

import com.typesafe.config.ConfigFactory


object Settings {
  private val config = ConfigFactory.load
  lazy val clientId = reddit.ClientId(config.getString("reddit.client-id"))
  lazy val clientSecret = reddit.ClientSecret(config.getString("reddit.client-secret"))
  lazy val authConfig = reddit.AuthConfig(clientId, clientSecret, "http://localhost:8080/redirect")
}
