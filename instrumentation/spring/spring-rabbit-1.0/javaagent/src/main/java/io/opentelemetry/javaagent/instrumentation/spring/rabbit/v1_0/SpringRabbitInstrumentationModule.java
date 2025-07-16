/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringRabbitInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringRabbitInstrumentationModule() {
    super("spring-rabbit", "spring-rabbit-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractMessageListenerContainerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
