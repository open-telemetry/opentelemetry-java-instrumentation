/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javalin.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class JavalinInstrumentationModule extends InstrumentationModule {

  public JavalinInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "javalin-5.0" : "javalin",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"javalin"}
            : new String[] {"javalin-5.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JavalinInstrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 5.0.0
    return hasClassesNamed("io.javalin.config.JavalinConfig")
        // added in 7.0.0
        .and(not(hasClassesNamed("io.javalin.config.JavalinState")));
  }
}
