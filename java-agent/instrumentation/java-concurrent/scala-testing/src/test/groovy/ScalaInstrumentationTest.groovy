import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

class ScalaInstrumentationTest extends AgentTestRunner {

  def "scala futures and callbacks"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.traceWithFutureAndCallbacks()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.traceWithFutureAndCallbacks"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("goodFuture") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("badFuture") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("successCallback") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("failureCallback") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "scala propagates across futures with no traces"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.tracedAcrossThreadsWithNoTrace()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedAcrossThreadsWithNoTrace"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("callback") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "scala either promise completion"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.traceWithPromises()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.traceWithPromises"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("future1") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("keptPromise") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("keptPromise2") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("brokenPromise") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "scala first completed future"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.tracedWithFutureFirstCompletions()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedWithFutureFirstCompletions"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout1") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout2") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
        span("timeout3") {
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "ScalaConcurrentTests.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }
}
