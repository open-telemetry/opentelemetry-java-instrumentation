/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class IbmMqInstrumentationModule extends InstrumentationModule {

  public IbmMqInstrumentationModule() {
    super("ibmmq", "ibm-mq");
  }

  @Override
  public int order() {
    // Apply after the generic JMS instrumentation so that its span is the one being enriched.
    return 1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        // classic com.ibm.mq base API (owns its own spans)
        new IbmMqConnectionInstrumentation(),
        new IbmMqProducerInstrumentation(),
        // IBM MQ JMS provider: additive enrichment of the generic JMS spans
        new IbmMqJmsProducerInstrumentation(),
        // async MessageListener consumers: capture at registration, stamp at delivery
        new IbmMqJmsSetListenerInstrumentation(),
        new IbmMqJmsListenerInstrumentation());
    // NOTE: synchronous MessageConsumer.receive() is deliberately NOT instrumented. The generic
    // JMS instrumentation creates that span in its own exit advice via startAndEnd (created and
    // ended in one call, never made current), so no advice here can reach it. Writing to
    // Span.current() there would silently stamp whatever unrelated span happened to be active.
  }
}
