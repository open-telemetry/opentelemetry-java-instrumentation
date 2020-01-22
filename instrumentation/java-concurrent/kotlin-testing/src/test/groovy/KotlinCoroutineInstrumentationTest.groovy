import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadPoolDispatcherKt

class KotlinCoroutineInstrumentationTest extends AgentTestRunner {

  static dispatchersToTest = [
    Dispatchers.Default,
    Dispatchers.IO,
    Dispatchers.Unconfined,
    ThreadPoolDispatcherKt.newFixedThreadPoolContext(2, "Fixed-Thread-Pool"),
    ThreadPoolDispatcherKt.newSingleThreadContext("Single-Thread"),
  ]

  def "kotlin traced across channels"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedAcrossChannels()

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedAcrossChannels"
            "$Tags.COMPONENT" "trace"
          }
        }
        (0..2).each {
          span("produce_$it") {
            childOf span(0)
            tags {
              "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
              "$Tags.COMPONENT" "trace"
            }
          }
          span("consume_$it") {
            childOf span(0)
            tags {
              "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
              "$Tags.COMPONENT" "trace"
            }
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin cancellation prevents trace"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracePreventedByCancellation()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracePreventedByCancellation"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("preLaunch") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin propagates across nested jobs"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedAcrossThreadsWithNested()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedAcrossThreadsWithNested"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("nested") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin either deferred completion"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(Dispatchers.Default)

    when:
    kotlinTest.traceWithDeferred()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.traceWithDeferred"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("future1") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("keptPromise") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("keptPromise2") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("brokenPromise") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin first completed deferred"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(Dispatchers.Default)

    when:
    kotlinTest.tracedWithDeferredFirstCompletions()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedWithDeferredFirstCompletions"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout1") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout2") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout3") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "KotlinCoroutineTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }
}
