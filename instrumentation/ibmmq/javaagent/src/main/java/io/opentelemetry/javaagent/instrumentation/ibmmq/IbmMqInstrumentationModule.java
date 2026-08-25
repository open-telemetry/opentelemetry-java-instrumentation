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
        // Kept separate from the jakarta module -- see IbmMqQmidSupport for why.
        new IbmMqJmsProducerInstrumentation(),
        new IbmMqJmsSetListenerInstrumentation(),
        new IbmMqJmsListenerInstrumentation(),
        new IbmMqJmsReceiveInstrumentation());
  }
}
