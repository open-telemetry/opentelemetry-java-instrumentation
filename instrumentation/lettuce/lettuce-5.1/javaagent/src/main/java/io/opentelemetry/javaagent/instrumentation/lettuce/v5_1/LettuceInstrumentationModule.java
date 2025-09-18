/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public LettuceInstrumentationModule() {
    super("lettuce", "lettuce-5.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("io.lettuce.core.tracing.Tracing");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lettuce.core.protocol.OtelCommandArgsUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultClientResourcesInstrumentation(), new LettuceAsyncCommandInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
