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

abstract class ItemLevelSpanTest extends AgentInstrumentationSpecification {
  abstract runJob(String jobName, Map<String, JobParameter> params = emptyMap())

  def "should trace item read, process and write calls"() {
    when:
    runJob("itemsAndTaskletJob")

    then:
    assertTraces(1) {
      trace(0, 37) {
        span(0) {
          name "BatchJob itemsAndTaskletJob"
          kind INTERNAL
        }

        // item step
        span(1) {
          name "BatchJob itemsAndTaskletJob.itemStep"
          kind INTERNAL
          childOf span(0)
        }

        // chunk 1, items 0-5
        span(2) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        (3..7).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(2)
          }
        }
        (8..12).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(2)
          }
        }
        span(13) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(2)
        }

        // chunk 2, items 5-10
        span(14) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        (15..19).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(14)
          }
        }
        (20..24).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(14)
          }
        }
        span(25) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(14)
        }

        // chunk 3, items 10-13
        span(26) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        // +1 for last read returning end of stream marker
        (27..30).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(26)
          }
        }
        (31..33).forEach {
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(26)
          }
        }
        span(34) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(26)
        }

        // tasklet step
        span(35) {
          name "BatchJob itemsAndTaskletJob.taskletStep"
          kind INTERNAL
          childOf span(0)
        }
        span(36) {
          name "BatchJob itemsAndTaskletJob.taskletStep.Chunk"
          kind INTERNAL
          childOf span(35)
        }
      }
    }
  }

  def "should trace all item operations on a parallel items job"() {
    when:
    runJob("parallelItemsJob")

    then:
    assertTraces(1) {
      trace(0, 23) {
        span(0) {
          name "BatchJob parallelItemsJob"
          kind INTERNAL
        }
        span(1) {
          name "BatchJob parallelItemsJob.parallelItemsStep"
          kind INTERNAL
          childOf span(0)
        }

        // chunk 1, first two items; thread 1
        span(2) {
          name "BatchJob parallelItemsJob.parallelItemsStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        [3, 4].forEach {
          span(it) {
            name "BatchJob parallelItemsJob.parallelItemsStep.ItemRead"
            kind INTERNAL
            childOf span(2)
          }
        }
        [5, 6].forEach {
          span(it) {
            name "BatchJob parallelItemsJob.parallelItemsStep.ItemProcess"
            kind INTERNAL
            childOf span(2)
          }
        }
        span(7) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemWrite"
          kind INTERNAL
          childOf span(2)
        }

        // chunk 2, items 3 & 4; thread 2
        span(8) {
          name "BatchJob parallelItemsJob.parallelItemsStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        [9, 10].forEach {
          span(it) {
            name "BatchJob parallelItemsJob.parallelItemsStep.ItemRead"
            kind INTERNAL
            childOf span(8)
          }
        }
        [11, 12].forEach {
          span(it) {
            name "BatchJob parallelItemsJob.parallelItemsStep.ItemProcess"
            kind INTERNAL
            childOf span(8)
          }
        }
        span(13) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemWrite"
          kind INTERNAL
          childOf span(8)
        }

        // chunk 3, 5th item; thread 1
        span(14) {
          name "BatchJob parallelItemsJob.parallelItemsStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        // +1 for last read returning end of stream marker
        [15, 16].forEach {
          span(it) {
            name "BatchJob parallelItemsJob.parallelItemsStep.ItemRead"
            kind INTERNAL
            childOf span(14)
          }
        }
        span(17) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemProcess"
          kind INTERNAL
          childOf span(14)
        }
        span(18) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemWrite"
          kind INTERNAL
          childOf span(14)
        }

        // empty chunk on thread 2, end processing
        span(19) {
          name "BatchJob parallelItemsJob.parallelItemsStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        // end of stream marker
        span(20) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemRead"
          kind INTERNAL
          childOf span(19)
        }

        // empty chunk on thread 1, end processing
        span(21) {
          name "BatchJob parallelItemsJob.parallelItemsStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        // end of stream marker
        span(22) {
          name "BatchJob parallelItemsJob.parallelItemsStep.ItemRead"
          kind INTERNAL
          childOf span(21)
        }
      }
    }
  }
}

class JavaConfigItemLevelSpanTest extends ItemLevelSpanTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new AnnotationConfigApplicationContext(SpringBatchApplication)
  }
}

class XmlConfigItemLevelSpanTest extends ItemLevelSpanTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new ClassPathXmlApplicationContext("spring-batch.xml")
  }
}

// JsrChunkProcessor works a bit differently than the "standard" one and does not read the whole
// chunk at once, it reads every item separately; it results in a different span ordering, that's
// why it has a completely separate test class
class JsrConfigItemLevelSpanTest extends AgentInstrumentationSpecification implements JavaxBatchConfigTrait {
  def "should trace item read, process and write calls"() {
    when:
    runJob("itemsAndTaskletJob", [:])

    then:
    assertTraces(1) {
      trace(0, 37) {
        span(0) {
          name "BatchJob itemsAndTaskletJob"
          kind INTERNAL
        }

        // item step
        span(1) {
          name "BatchJob itemsAndTaskletJob.itemStep"
          kind INTERNAL
          childOf span(0)
        }

        // chunk 1, items 0-5
        span(2) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        (3..11).step(2) {
          println it
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(2)
          }
          span(it + 1) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(2)
          }
        }
        span(13) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(2)
        }

        // chunk 2, items 5-10
        span(14) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        (15..23).step(2) {
          println it
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(14)
          }
          span(it + 1) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(14)
          }
        }
        span(25) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(14)
        }

        // chunk 3, items 10-13
        span(26) {
          name "BatchJob itemsAndTaskletJob.itemStep.Chunk"
          kind INTERNAL
          childOf span(1)
        }
        (27..32).step(2) {
          println it
          span(it) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
            kind INTERNAL
            childOf span(26)
          }
          span(it + 1) {
            name "BatchJob itemsAndTaskletJob.itemStep.ItemProcess"
            kind INTERNAL
            childOf span(26)
          }
        }
        // last read returning end of stream marker
        span(33) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemRead"
          kind INTERNAL
          childOf span(26)
        }
        span(34) {
          name "BatchJob itemsAndTaskletJob.itemStep.ItemWrite"
          kind INTERNAL
          childOf span(26)
        }

        // tasklet step
        span(35) {
          name "BatchJob itemsAndTaskletJob.taskletStep"
          kind INTERNAL
          childOf span(0)
        }
        span(36) {
          name "BatchJob itemsAndTaskletJob.taskletStep.Chunk"
          kind INTERNAL
          childOf span(35)
        }
      }
    }
  }
}
