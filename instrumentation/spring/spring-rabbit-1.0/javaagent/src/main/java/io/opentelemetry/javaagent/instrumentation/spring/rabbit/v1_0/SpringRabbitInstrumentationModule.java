/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringRabbitInstrumentationModule extends InstrumentationModule {
  public SpringRabbitInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-rabbit-1.0" : "spring-rabbit",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-rabbit"}
            : new String[] {"spring-rabbit-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractMessageListenerContainerInstrumentation(),
        new SpringRabbitConsumerInstrumentation(),
        new SimpleMessageListenerContainerInstrumentation());
  }
}
