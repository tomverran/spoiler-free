package io.tvc.spoilerfree.reddit

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.scanamo.ScanamoAsync
import com.gu.scanamo.error.DynamoReadError
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.AuthError.UnknownError

import scala.concurrent.Future


class UserStore extends LazyLogging {

  private lazy val dynamo = AmazonDynamoDBAsyncClientBuilder.defaultClient()

  def all: Future[List[User]] =
    ScanamoAsync.scan[User](dynamo)("spoiler-free-users").map { results =>
      results.flatMap {
        case Right(u) =>
          List(u)
        case Left(e) =>
          logger.error(DynamoReadError.describe(e))
          List.empty[User]
      }
    }

  def put(user: User): Future[Either[AuthError, Unit]] =
    ScanamoAsync.put(dynamo)("spoiler-free-users")(user).map {
      _ => Right(())
    }.recover {
      case e: Throwable => Left(UnknownError(e.getMessage))
    }
}
