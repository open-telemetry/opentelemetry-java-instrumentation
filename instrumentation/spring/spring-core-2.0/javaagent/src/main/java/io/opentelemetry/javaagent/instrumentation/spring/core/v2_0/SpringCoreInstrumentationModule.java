/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringCoreInstrumentationModule extends InstrumentationModule {
  public SpringCoreInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-core-2.0" : "spring-core",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-core"}
            : new String[] {"spring-core-2.0"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.0
    return hasClassesNamed("org.springframework.core.task.SimpleAsyncTaskExecutor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SimpleAsyncTaskExecutorInstrumentation());
  }
}
