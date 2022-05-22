/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.springframework.context.annotation.AnnotationConfigApplicationContext

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpringSchedulingTest extends AgentInstrumentationSpecification {

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
          name "TriggerTask.run"
          hasNoParent()
          attributes {
            "code.namespace" "TriggerTask"
            "code.function" "run"
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
          name "IntervalTask.run"
          hasNoParent()
          attributes {
            "code.namespace" "IntervalTask"
            "code.function" "run"
          }
        }
      }
    }
  }

  def "schedule lambda test"() {
    setup:
    def context = new AnnotationConfigApplicationContext(LambdaTaskConfig)
    def configurer = context.getBean(LambdaTaskConfigurer)

    configurer.singleUseLatch.await(2000, TimeUnit.MILLISECONDS)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          nameContains "LambdaTaskConfigurer\$\$Lambda\$"
          hasNoParent()
          attributes {
            "code.namespace" { it.contains("LambdaTaskConfigurer\$\$Lambda\$") }
            "code.function" "run"
          }
        }
      }
    }

    cleanup:
    context.close()
  }

  // by putting the scheduled method directly on the TaskConfig, this verifies the case where the
  // class is enhanced and so has a different class name, e.g. TaskConfig$$EnhancerByCGLIB$$b910c4a9
  def "schedule enhanced class test"() {
    setup:
    def context = new AnnotationConfigApplicationContext(EnhancedClassTaskConfig)
    def latch = context.getBean(CountDownLatch)

    latch.await(5, TimeUnit.SECONDS)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "EnhancedClassTaskConfig.run"
          hasNoParent()
          attributes {
            "code.namespace" "EnhancedClassTaskConfig"
            "code.function" "run"
          }
        }
      }
    }
  }
}
