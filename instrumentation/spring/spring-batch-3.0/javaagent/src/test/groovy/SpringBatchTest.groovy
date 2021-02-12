/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static java.util.Collections.emptyMap

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.springframework.batch.core.JobParameter
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

abstract class SpringBatchTest extends AgentInstrumentationSpecification {

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

  def "should trace flow job"() {
    when:
    runJob("flowJob")

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "BatchJob flowJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob flowJob.flowStep1"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "BatchJob flowJob.flowStep1.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name "BatchJob flowJob.flowStep2"
          kind INTERNAL
          childOf span(0)
        }
        span(4) {
          name "BatchJob flowJob.flowStep2.Chunk"
          kind INTERNAL
          childOf span(3)
        }
      }
    }
  }

  def "should trace split flow job"() {
    when:
    runJob("splitJob")

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "BatchJob splitJob"
          kind INTERNAL
        }
        span(1) {
          name ~/BatchJob splitJob\.splitFlowStep[12]/
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name ~/BatchJob splitJob\.splitFlowStep[12]\.Chunk/
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name ~/BatchJob splitJob\.splitFlowStep[12]/
          kind INTERNAL
          childOf span(0)
        }
        span(4) {
          name ~/BatchJob splitJob\.splitFlowStep[12]\.Chunk/
          kind INTERNAL
          childOf span(3)
        }
      }
    }
  }

  def "should trace job with decision"() {
    when:
    runJob("decisionJob")

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "BatchJob decisionJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob decisionJob.decisionStepStart"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "BatchJob decisionJob.decisionStepStart.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name "BatchJob decisionJob.decisionStepLeft"
          kind INTERNAL
          childOf span(0)
        }
        span(4) {
          name "BatchJob decisionJob.decisionStepLeft.Chunk"
          kind INTERNAL
          childOf span(3)
        }
      }
    }
  }

  def "should trace partitioned job"() {
    when:
    runJob("partitionedJob")

    then:
    assertTraces(1) {
      trace(0, 8) {
        span(0) {
          name "BatchJob partitionedJob"
          kind INTERNAL
        }
        span(1) {
          def stepName = hasPartitionManagerStep() ? "partitionManagerStep" : "partitionWorkerStep"
          name "BatchJob partitionedJob.$stepName"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01]/
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01].Chunk/
          kind INTERNAL
          childOf span(2)
        }
        span(4) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01].Chunk/
          kind INTERNAL
          childOf span(2)
        }
        span(5) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01]/
          kind INTERNAL
          childOf span(1)
        }
        span(6) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01].Chunk/
          kind INTERNAL
          childOf span(5)
        }
        span(7) {
          name ~/BatchJob partitionedJob.partitionWorkerStep:partition[01].Chunk/
          kind INTERNAL
          childOf span(5)
        }
      }
    }
  }

  protected boolean hasPartitionManagerStep() {
    true
  }
}

class JavaConfigBatchJobTest extends SpringBatchTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new AnnotationConfigApplicationContext(SpringBatchApplication)
  }
}

class XmlConfigBatchJobTest extends SpringBatchTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new ClassPathXmlApplicationContext("spring-batch.xml")
  }
}

class JsrConfigBatchJobTest extends SpringBatchTest implements JavaxBatchConfigTrait {
  protected boolean hasPartitionManagerStep() {
    false
  }
}
