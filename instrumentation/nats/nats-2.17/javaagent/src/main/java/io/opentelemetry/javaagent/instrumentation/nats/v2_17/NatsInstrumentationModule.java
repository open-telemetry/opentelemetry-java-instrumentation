/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class NatsInstrumentationModule extends InstrumentationModule {

  public NatsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "nats-2.17" : "nats",
        AgentCommonConfig.get().isV3Preview() ? new String[] {"nats"} : new String[] {"nats-2.17"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ConnectionPublishInstrumentation(),
        new ConnectionRequestInstrumentation(),
        new MessageHandlerInstrumentation());
  }
}
