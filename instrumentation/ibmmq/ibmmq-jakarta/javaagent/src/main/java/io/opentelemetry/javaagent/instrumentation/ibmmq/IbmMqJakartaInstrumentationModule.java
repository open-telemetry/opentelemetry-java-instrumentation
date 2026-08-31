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
public class IbmMqJakartaInstrumentationModule extends InstrumentationModule {

  public IbmMqJakartaInstrumentationModule() {
    super("ibmmq", "ibmmq-jakarta", "ibm-mq-jakarta");
  }

  @Override
  public int order() {
    // Apply after the generic JMS instrumentation, same as the javax module.
    return 1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new IbmMqJakartaJmsProducerInstrumentation(),
        new IbmMqJakartaJmsSetListenerInstrumentation(),
        new IbmMqJakartaJmsListenerInstrumentation(),
        new IbmMqJakartaJmsReceiveInstrumentation());
  }
}
