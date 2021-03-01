/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge
import slick.jdbc.H2Profile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class SlickUtils {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  import SlickUtils._

  val database = Database.forURL(
    Url,
    user = Username,
    driver = "org.h2.Driver",
    keepAliveConnection = true,
    // Limit number of threads to hit Slick-specific case when we need to avoid re-wrapping
    // wrapped runnables.
    executor = AsyncExecutor("test", numThreads = 1, queueSize = 1000)
  )
  Await.result(
    database.run(
      sqlu"""CREATE ALIAS IF NOT EXISTS SLEEP FOR "java.lang.Thread.sleep(long)""""
    ),
    Duration.Inf
  )

  def startQuery(query: String): Future[Vector[Int]] = {
    val span = tracer.spanBuilder("run query").startSpan()
    val scope = Java8BytecodeBridge.currentContext().`with`(span).makeCurrent()
    try {
      return database.run(sql"#$query".as[Int])
    } finally {
      span.end()
      scope.close()
    }
  }

  def getResults(future: Future[Vector[Int]]): Int = {
    Await.result(future, Duration.Inf).head
  }
}

object SlickUtils {

  val Driver = "h2"
  val Db = "test"
  val Username = "TESTUSER"
  val Url = s"jdbc:${Driver}:mem:${Db}"
  val TestValue = 3
  val TestQuery = "SELECT 3"

  val SleepQuery = "CALL SLEEP(3000)"

}
