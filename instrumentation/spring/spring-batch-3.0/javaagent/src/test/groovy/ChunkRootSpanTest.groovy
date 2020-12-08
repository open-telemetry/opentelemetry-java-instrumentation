/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.setConfig
import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.updateConfigAndResetInstrumentation
import static java.util.Collections.emptyMap

import io.opentelemetry.instrumentation.test.AgentTestRunner
import org.springframework.batch.core.JobParameter
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

abstract class ChunkRootSpanTest extends AgentTestRunner {

  abstract runJob(String jobName, Map<String, JobParameter> params = emptyMap())

  def "should create separate traces for each chunk"() {
    when:
    runJob("itemsAndTaskletJob")

    then:
    assertTraces(5) {
      def itemStepSpan = null
      def taskletStepSpan = null

      trace(0, 3) {
        itemStepSpan = span(1)
        taskletStepSpan = span(2)

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
          name "BatchJob itemsAndTaskletJob.taskletStep"
          kind INTERNAL
          childOf span(0)
        }
      }
      trace(1, 1) {
        span(0) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          hasLink itemStepSpan
        }
      }
      trace(2, 1) {
        span(0) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          hasLink itemStepSpan
        }
      }
      trace(3, 1) {
        span(0) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          hasLink itemStepSpan
        }
      }
      trace(4, 1) {
        span(0) {
          name "BatchJob itemsAndTaskletJob.taskletStep.Chunk"
          kind INTERNAL
          hasLink taskletStepSpan
        }
      }
    }
  }
}

class JavaConfigChunkRootSpanTest extends ChunkRootSpanTest implements ApplicationConfigTrait {
  static final PREVIOUS_CONFIG = updateConfigAndResetInstrumentation({
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
    it.setProperty("otel.instrumentation.spring-batch.chunk.root-span", "true")
  })

  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new AnnotationConfigApplicationContext(SpringBatchApplication)
  }

  @Override
  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }
}

class XmlConfigChunkRootSpanTest extends ChunkRootSpanTest implements ApplicationConfigTrait {
  static final PREVIOUS_CONFIG = updateConfigAndResetInstrumentation({
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
    it.setProperty("otel.instrumentation.spring-batch.chunk.root-span", "true")
  })

  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new ClassPathXmlApplicationContext("spring-batch.xml")
  }

  @Override
  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }
}

class JsrConfigChunkRootSpanTest extends ChunkRootSpanTest implements JavaxBatchConfigTrait {
  static final PREVIOUS_CONFIG = updateConfigAndResetInstrumentation({
    it.setProperty("otel.instrumentation.spring-batch.enabled", "true")
    it.setProperty("otel.instrumentation.spring-batch.chunk.root-span", "true")
  })

  @Override
  def additionalCleanup() {
    setConfig(PREVIOUS_CONFIG)
  }
}
