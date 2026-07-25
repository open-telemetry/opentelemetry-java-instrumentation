/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@Deprecated // superseded by the lettuce-5.0 javaagent under v3-preview; to be removed in 3.0
@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {

  public LettuceInstrumentationModule() {
    super("lettuce", "lettuce-5.1");
  }

  @Override
  public boolean defaultEnabled() {
    // disabled under v3-preview, where the lettuce-5.0 javaagent module covers 5.1+
    return super.defaultEnabled() && !AgentCommonConfig.get().isV3Preview();
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.1
    return hasClassesNamed("io.lettuce.core.tracing.Tracing");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.lettuce.core.protocol.OtelCommandArgsUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientResourcesInstrumentation(), new LettuceAsyncCommandInstrumentation());
  }
}
