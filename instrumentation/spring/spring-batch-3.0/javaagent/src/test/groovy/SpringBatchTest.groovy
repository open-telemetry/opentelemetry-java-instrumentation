/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.setConfig
import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.updateConfigAndResetInstrumentation
import static java.util.Collections.emptyMap

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.test.AgentTestRunner
import org.springframework.batch.core.JobParameter
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

abstract class SpringBatchTest extends AgentTestRunner {

  abstract runJob(String jobName, Map<String, JobParameter> params = emptyMap())

  def "should trace tasklet job+step"() {
    when:
    runJob("taskletJob")

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "BatchJob taskletJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob taskletJob.step"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "BatchJob taskletJob.step.Chunk"
          kind INTERNAL
          childOf span(1)
        }
      }
    }
  }

  def "should handle exception in tasklet job+step"() {
    when:
    runJob("taskletJob", ["fail": new JobParameter(1)])

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "BatchJob taskletJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob taskletJob.step"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "BatchJob taskletJob.step.Chunk"
          kind INTERNAL
          childOf span(1)
          errored true
          errorEvent RuntimeException, "fail"
        }
      }
    }
  }

  def "should trace chunked items job"() {
    when:
    runJob("itemsAndTaskletJob")

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          name "BatchJob itemsAndTaskletJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob itemsAndTaskletJob.itemStep"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        span(4) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        span(5) {
          name "BatchJob itemsAndTaskletJob.taskletStep"
          kind INTERNAL
          childOf span(0)
        }
        span(6) {
          name "BatchJob itemsAndTaskletJob.taskletStep.Chunk"
          kind INTERNAL
          childOf span(5)
        }
      }
    }
  }
}

class JavaConfigBatchJobTest extends SpringBatchTest implements ApplicationConfigTrait {
  static final Config PREVIOUS_CONFIG = updateConfigAndResetInstrumentation {
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
  }

  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }

  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new AnnotationConfigApplicationContext(SpringBatchApplication)
  }
}

class XmlConfigBatchJobTest extends SpringBatchTest implements ApplicationConfigTrait {
  static final Config PREVIOUS_CONFIG = updateConfigAndResetInstrumentation {
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
  }

  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }

  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new ClassPathXmlApplicationContext("spring-batch.xml")
  }
}

class JsrConfigBatchJobTest extends SpringBatchTest implements JavaxBatchConfigTrait {
  static final Config PREVIOUS_CONFIG = updateConfigAndResetInstrumentation {
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
  }

  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }
}
