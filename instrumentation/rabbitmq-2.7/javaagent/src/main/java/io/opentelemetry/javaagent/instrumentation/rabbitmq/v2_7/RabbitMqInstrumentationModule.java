/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RabbitMqInstrumentationModule extends InstrumentationModule {
  public RabbitMqInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "rabbitmq-2.7" : "rabbitmq",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"rabbitmq"}
            : new String[] {"rabbitmq-2.7"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RabbitChannelInstrumentation(),
        new RabbitCommandInstrumentation(),
        new RabbitConnectionInstrumentation());
  }
}
