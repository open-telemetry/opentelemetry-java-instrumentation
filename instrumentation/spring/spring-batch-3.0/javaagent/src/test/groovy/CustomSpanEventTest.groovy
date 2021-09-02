/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import org.springframework.batch.core.JobParameter
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static java.util.Collections.emptyMap

abstract class CustomSpanEventTest extends AgentInstrumentationSpecification {
  static final boolean VERSION_GREATER_THAN_4_0 = Boolean.getBoolean("testLatestDeps")

  abstract runJob(String jobName, Map<String, JobParameter> params = emptyMap())

  def "should be able to call Span.current() and add custom info to spans"() {
    when:
    runJob("customSpanEventsItemsJob")

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          name "BatchJob customSpanEventsItemsJob"
          kind INTERNAL
          events(2)
          event(0) {
            eventName "job.before"
          }
          event(1) {
            eventName "job.after"
          }
        }
        span(1) {
          name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep"
          kind INTERNAL
          childOf span(0)

          // CompositeChunkListener has broken ordering that causes listeners that do not override order() to appear first at all times
          // because of that a custom ChunkListener will always see a Step span when using spring-batch versions [3, 4)
          // that bug was fixed in 4.0
          if (VERSION_GREATER_THAN_4_0) {
            events(2)
            event(0) {
              eventName "step.before"
            }
            event(1) {
              eventName "step.after"
            }
          } else {
            events(4)
            event(0) {
              eventName "step.before"
            }
            event(1) {
              eventName "chunk.before"
            }
            event(2) {
              eventName "chunk.after"
            }
            event(3) {
              eventName "step.after"
            }
          }
        }
        span(2) {
          name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.Chunk"
          kind INTERNAL
          childOf span(1)

          // CompositeChunkListener has broken ordering that causes listeners that do not override order() to appear first at all times
          // because of that a custom ChunkListener will always see a Step span when using spring-batch versions [3, 4)
          // that bug was fixed in 4.0
          if (VERSION_GREATER_THAN_4_0) {
            events(2)
            event(0) {
              eventName "chunk.before"
            }
            event(1) {
              eventName "chunk.after"
            }
          } else {
            events(0)
          }
        }

        itemSpans(it)
      }
    }
  }

  // Spring Batch Java & XML configs have slightly different ordering from JSR config
  protected void itemSpans(TraceAssert trace) {
    trace.with {
      span(3) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.read.before"
        }
        event(1) {
          eventName "item.read.after"
        }
      }
      // second read that returns null and signifies end of stream
      span(4) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead"
        kind INTERNAL
        childOf span(2)
        // spring batch does not call ItemReadListener after() methods when read() returns end-of-stream
        events(1)
        event(0) {
          eventName "item.read.before"
        }
      }
      span(5) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemProcess"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.process.before"
        }
        event(1) {
          eventName "item.process.after"
        }
      }
      span(6) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemWrite"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.write.before"
        }
        event(1) {
          eventName "item.write.after"
        }
      }
    }
  }
}

class JavaConfigCustomSpanEventTest extends CustomSpanEventTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new AnnotationConfigApplicationContext(SpringBatchApplication)
  }
}

class XmlConfigCustomSpanEventTest extends CustomSpanEventTest implements ApplicationConfigTrait {
  @Override
  ConfigurableApplicationContext createApplicationContext() {
    new ClassPathXmlApplicationContext("spring-batch.xml")
  }
}

class JsrConfigCustomSpanEventTest extends CustomSpanEventTest implements JavaxBatchConfigTrait {

  // JSR config has different item span ordering
  protected void itemSpans(TraceAssert trace) {
    trace.with {
      span(3) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.read.before"
        }
        event(1) {
          eventName "item.read.after"
        }
      }
      span(4) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemProcess"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.process.before"
        }
        event(1) {
          eventName "item.process.after"
        }
      }
      // second read that returns null and signifies end of stream
      span(5) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead"
        kind INTERNAL
        childOf span(2)
        // spring batch does not call ItemReadListener after() methods when read() returns end-of-stream
        events(1)
        event(0) {
          eventName "item.read.before"
        }
      }
      span(6) {
        name "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemWrite"
        kind INTERNAL
        childOf span(2)
        events(2)
        event(0) {
          eventName "item.write.before"
        }
        event(1) {
          eventName "item.write.after"
        }
      }
    }
  }
}
