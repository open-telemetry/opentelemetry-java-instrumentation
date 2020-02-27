import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class SpringSchedulingTest extends AgentTestRunner {

  def "schedule trigger test according to cron expression"() {
    setup:
    def context = new AnnotationConfigApplicationContext(TriggerTaskConfig)
    def task = context.getBean(TriggerTask)

    task.blockUntilExecute()

    expect:
    assert task != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "scheduled.call"
          parent()
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "TriggerTask.run"
            "$Tags.COMPONENT" "spring-scheduling"
          }
        }
      }
    }
  }

  def "schedule interval test"() {
    setup:
    def context = new AnnotationConfigApplicationContext(IntervalTaskConfig)
    def task = context.getBean(IntervalTask)

    task.blockUntilExecute()

    expect:
    assert task != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "scheduled.call"
          parent()
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "IntervalTask.run"
            "$Tags.COMPONENT" "spring-scheduling"
          }
        }
      }
    }

  }
}
