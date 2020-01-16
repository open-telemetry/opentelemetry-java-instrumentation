import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.instrumentation.api.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class ReportTimeTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.clearProperty("dd.trace.annotations")
    }
  }

  def "test method executed"() {
    setup:
    def scheduledTask = new ScheduledTasks();
    scheduledTask.reportCurrentTime();

    expect:
    assert scheduledTask.reportCurrentTimeExecuted == true;
  }

  def "trace fired"() {
    setup:
    def scheduledTask = new ScheduledTasks();
    scheduledTask.reportCurrentTime();

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "test"
          resourceName "ScheduledTasks.reportCurrentTime"
          operationName "trace.annotation"
          parent()
          errored false
          tags {
//            "$Tags.COMPONENT" "scheduled"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test with context"() {
    setup:
    def context = new AnnotationConfigApplicationContext(ScheduledTasksConfig) // provide config as argument
    def task = context.getBean(ScheduledTasks) // provide class we are trying to test
    task.reportCurrentTime()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "test"
          resourceName "ScheduledTasks.reportCurrentTime"
          operationName "trace.annotation"
          parent()
          errored false
          tags {
//            "$Tags.COMPONENT" "scheduled"
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
      }
    }
  }
}
