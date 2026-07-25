/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx.LettuceReactiveCommandsInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {
  public LettuceInstrumentationModule() {
    super("lettuce", "lettuce-5.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    if (AgentCommonConfig.get().isV3Preview()) {
      // under v3-preview this advice-based module handles the whole supported lettuce range (5.0+);
      // the SPI-based lettuce-5.1 javaagent module is disabled
      return hasClassesNamed("io.lettuce.core.AbstractRedisAsyncCommands");
    }
    // the tracing SPI was added in 5.1; by default this module only handles pre-5.1, while the
    // lettuce-5.1 javaagent module handles 5.1+
    return not(hasClassesNamed("io.lettuce.core.tracing.Tracing"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new LettuceAsyncCommandInstrumentation(),
        new LettuceAsyncCommandsInstrumentation(),
        new LettuceEndpointInstrumentation(),
        new LettuceClientInstrumentation(),
        new LettuceReactiveCommandsInstrumentation());
  }
}
