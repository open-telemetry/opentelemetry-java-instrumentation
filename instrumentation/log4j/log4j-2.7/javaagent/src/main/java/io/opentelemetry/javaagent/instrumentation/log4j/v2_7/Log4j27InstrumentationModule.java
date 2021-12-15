/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v2_7;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Log4j27InstrumentationModule extends InstrumentationModule {
  public Log4j27InstrumentationModule() {
    super("log4j", "log4j-2.7");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // want to cover 2.7 through (and including) 2.15.0 (but not including 2.16.0+)
    // - ContextDataInjectorFactory introduced in 2.7
    // - DefaultArbiter introduced in 2.15.0
    // - LookupMessagePatternConverter introduced in 2.15.0, removed in 2.16.0
    return hasClassesNamed("org.apache.logging.log4j.core.impl.ContextDataInjectorFactory")
        .and(
            not(hasClassesNamed("org.apache.logging.log4j.core.config.arbiters.DefaultArbiter"))
                .or(
                    hasClassesNamed(
                        "org.apache.logging.log4j.core.pattern.MessagePatternConverter$LookupMessagePatternConverter")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextDataInjectorFactoryInstrumentation());
  }
}
