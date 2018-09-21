import datadog.trace.api.Trace
import datadog.trace.context.TraceScope
import io.opentracing.util.GlobalTracer
import slick.jdbc.H2Profile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class SlickUtils {

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

  @Trace
  def startQuery(query: String): Future[Vector[Int]] = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
    database.run(sql"#$query".as[Int])
  }

  def getResults(future: Future[Vector[Int]]): Int = {
    Await.result(future, Duration.Inf).head
  }

}

object SlickUtils {

  val Driver = "h2"
  val Username = "TESTUSER"
  val Url = s"jdbc:${Driver}:mem:test"
  val TestValue = 3
  val TestQuery = "SELECT 3"

  val SleepQuery = "CALL SLEEP(3000)"

}
