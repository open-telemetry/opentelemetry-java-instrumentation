/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx.LettuceReactiveCommandsInstrumentation;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx.LettuceReactiveSubscriptionInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {
  public LettuceInstrumentationModule() {
    super("lettuce-5.0", "lettuce");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LettuceAsyncCommandInstrumentation(),
        new LettuceAsyncCommandsInstrumentation(),
        new LettuceCommandEncoderInstrumentation(),
        new LettuceEndpointInstrumentation(),
        new LettuceClientInstrumentation(),
        new LettuceClusterClientInstrumentation(),
        new LettuceMasterSlaveInstrumentation(),
        new LettuceReactiveSubscriptionInstrumentation(),
        new LettuceReactiveCommandsInstrumentation());
  }
}
