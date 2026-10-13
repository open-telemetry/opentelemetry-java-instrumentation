/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {
  public LettuceInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "lettuce-4.0" : "lettuce",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"lettuce"}
            : new String[] {"lettuce-4.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LettuceAbstractRedisClientInstrumentation(),
        new LettuceAsyncCommandInstrumentation(),
        new LettuceAsyncCommandsInstrumentation(),
        new LettuceCommandHandlerInstrumentation(),
        new LettuceCommandWrapperInstrumentation(),
        new LettuceReactiveCommandDispatcherInstrumentation(),
        new LettuceObservableCommandInstrumentation(),
        new LettuceConnectInstrumentation(),
        new LettuceClusterClientInstrumentation(),
        new LettuceMasterSlaveInstrumentation());
  }
}
