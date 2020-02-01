import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.api.Tags
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
          resourceName "TriggerTask.run"
          operationName "scheduled.call"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT" "spring-scheduling"
            defaultTags()
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
          resourceName "IntervalTask.run"
          operationName "scheduled.call"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT" "spring-scheduling"
            defaultTags()
          }
        }
      }
    }

  }
}
