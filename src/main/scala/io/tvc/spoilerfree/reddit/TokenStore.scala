package io.tvc.spoilerfree.reddit

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.gu.scanamo.ScanamoAsync
import com.gu.scanamo.error.DynamoReadError
import com.typesafe.scalalogging.LazyLogging
import io.tvc.spoilerfree.reddit.TokenStore.TokenStoreError
import io.tvc.spoilerfree.settings.dynamoTable

import scala.concurrent.{ExecutionContext, Future}


object TokenStore {
  case class TokenStoreError(underlying: Throwable)
}

class TokenStore(implicit ec: ExecutionContext) extends LazyLogging {

  private lazy val dynamo = AmazonDynamoDBAsyncClientBuilder.defaultClient()

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

  def put(token: RefreshToken): Future[Either[TokenStoreError, Unit]] =
    ScanamoAsync.put(dynamo)(dynamoTable)(token).map {
      _ => Right(())
    }.recover {
      case e: Throwable => Left(TokenStoreError(e))
    }
}
