import io.opentelemetry.auto.agent.test.AgentTestRunner
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags

class AkkaActorTest extends AgentTestRunner {

  def "akka #testMethod"() {
    setup:
    AkkaActors akkaTester = new AkkaActors()
    akkaTester."$testMethod"()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "trace.annotation"
          tags {
            "$MoreTags.RESOURCE_NAME" "AkkaActors.$testMethod"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "$expectedGreeting, Akka"
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "AkkaActors\$.tracedChild"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    testMethod     | expectedGreeting
    "basicTell"    | "Howdy"
    "basicAsk"     | "Howdy"
    "basicForward" | "Hello"
  }
}
