package io.tvc.spoilerfree.reddit

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.TokenStore.TokenStoreError
import io.tvc.spoilerfree.settings.dynamoTable

import scala.concurrent.{ExecutionContext, Future}


object TokenStore {
  case class TokenStoreError(underlying: Throwable)
}

class TokenStore(implicit ec: ExecutionContext) extends LazyLogging {
  private lazy val dynamo = AmazonDynamoDBAsyncClientBuilder
    .standard()
    .withRegion(Regions.EU_WEST_1)
    .build()

  def all: Future[List[RefreshToken]] =
    ScanamoAsync.scan[RefreshToken](dynamo)(dynamoTable).map { results =>
      results.flatMap {
        case Right(u) =>
          List(u)
        case Left(e) =>
          logger.error(DynamoReadError.describe(e))
          List.empty[RefreshToken]
      }
    }

  private def rescue[A](f: Future[A]): Future[Either[TokenStoreError, Unit]] = {
    f.map {
      _ => Right(())
    }.recover {
      case e: Throwable =>
        Left(TokenStoreError(e))
    }
  }

  def put(token: RefreshToken): Future[Either[TokenStoreError, Unit]] =
    rescue(ScanamoAsync.put(dynamo)(dynamoTable)(token))

  def delete(token: RefreshToken): Future[Either[TokenStoreError, Unit]] =
    rescue(ScanamoAsync.delete(dynamo)(dynamoTable)('value -> token.value))
}
