/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SlickUtils {
  val TRACER: Tracer = OpenTelemetry.getTracerFactory.get("io.opentelemetry.auto")

  import SlickUtils._

  val database = Database.forURL(Url,
    user = Username,
    driver = "org.h2.Driver",
    keepAliveConnection = true,
    // Limit number of threads to hit Slick-specific case when we need to avoid re-wrapping
    // wrapped runnables.
    executor = AsyncExecutor("test", numThreads = 1, queueSize = 1000)
  )
  Await.result(database.run(sqlu"""CREATE ALIAS IF NOT EXISTS SLEEP FOR "java.lang.Thread.sleep(long)""""), Duration.Inf)

  def runQuery(query: String): Int = {
    val span = TRACER.spanBuilder("run query").startSpan()
    val scope = TRACER.withSpan(span)
    try {
      val future = database.run(sql"#$query".as[Int])
      Await.result(future, Duration.Inf).head
    } finally {
      span.end()
      scope.close()
    }
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
