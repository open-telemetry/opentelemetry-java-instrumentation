package datadog.trace.common.util

import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.Config
import datadog.trace.util.test.DDSpecification

class ThreadCpuTimeAccessTest extends DDSpecification {
  def "No thread CPU time provider - profiling enabled"() {
    setup:
    ConfigUtils.updateConfig {
      System.properties.setProperty("dd.${Config.PROFILING_ENABLED}", "true")
    }
    ThreadCpuTimeAccess.disableJmx()

    when:
    def threadCpuTime1 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()
    // burn some cpu
    def sum = 0
    for (int i = 0; i < 10_000; i++) {
      sum += i
    }
    def threadCpuTime2 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()

    then:
    sum > 0
    threadCpuTime1 == Long.MIN_VALUE
    threadCpuTime2 == Long.MIN_VALUE

    cleanup:
    ThreadCpuTimeAccess.disableJmx()
  }

  def "No thread CPU time provider - profiling disabled"() {
    setup:
    ConfigUtils.updateConfig {
      System.properties.setProperty("dd.${Config.PROFILING_ENABLED}", "false")
    }
    ThreadCpuTimeAccess.enableJmx()

    when:
    def threadCpuTime1 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()
    // burn some cpu
    def sum = 0
    for (int i = 0; i < 10_000; i++) {
      sum += i
    }
    def threadCpuTime2 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()

    then:
    sum > 0
    threadCpuTime1 == Long.MIN_VALUE
    threadCpuTime2 == Long.MIN_VALUE

    cleanup:
    ThreadCpuTimeAccess.disableJmx()
  }

  def "JMX thread CPU time provider"() {
    setup:
    ConfigUtils.updateConfig {
      System.properties.setProperty("dd.${Config.PROFILING_ENABLED}", "true")
    }
    ThreadCpuTimeAccess.enableJmx()

    when:
    def threadCpuTime1 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()
    // burn some cpu
    def sum = 0
    for (int i = 0; i < 10_000; i++) {
      sum += i
    }
    def threadCpuTime2 = ThreadCpuTimeAccess.getCurrentThreadCpuTime()

    then:
    sum > 0
    threadCpuTime1 != Long.MIN_VALUE
    threadCpuTime2 != Long.MIN_VALUE
    threadCpuTime2 > threadCpuTime1

    cleanup:
    ThreadCpuTimeAccess.disableJmx()
  }
}
