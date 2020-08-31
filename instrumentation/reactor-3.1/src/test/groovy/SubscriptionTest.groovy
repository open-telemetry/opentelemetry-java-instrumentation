import application.io.opentelemetry.extensions.auto.annotations.WithSpan
import io.opentelemetry.auto.test.AgentTestRunner
import java.util.concurrent.CountDownLatch
import reactor.core.publisher.Mono

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class SubscriptionTest extends AgentTestRunner {

  def "subscription test"() {
    when:
    CountDownLatch latch = new CountDownLatch(1)
    runUnderTrace("parent") {
      Mono<Connection> connection = Mono.create {
        it.success(new Connection())
      }
      connection.subscribe {
        it.query()
        latch.countDown()
      }
    }
    latch.await()

    then:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "Connection.query", span(0))
      }
    }

  }

  static class Connection {
    @WithSpan
    static int query() {
      return new Random().nextInt()
    }
  }
}
