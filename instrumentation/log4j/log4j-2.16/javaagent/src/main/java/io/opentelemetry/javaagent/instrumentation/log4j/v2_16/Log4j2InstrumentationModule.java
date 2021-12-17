/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v2_16;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Log4j2InstrumentationModule extends InstrumentationModule {
  public Log4j2InstrumentationModule() {
    super("log4j", "log4j-2.16");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register(
        "META-INF/services/org.apache.logging.log4j.core.util.ContextDataProvider");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // want to cover 2.16.0+
    // - DefaultArbiter introduced in 2.15.0
    // - LookupMessagePatternConverter introduced in 2.15.0, removed in 2.16.0
    return hasClassesNamed("org.apache.logging.log4j.core.config.arbiters.DefaultArbiter")
        .and(
            not(
                hasClassesNamed(
                    "org.apache.logging.log4j.core.pattern.MessagePatternConverter$LookupMessagePatternConverter")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResourceInjectingTypeInstrumentation());
  }

  // A type instrumentation is needed to trigger resource injection.
  public static class ResourceInjectingTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // we cannot use ContextDataProvider here because one of the classes that we inject implements
      // this interface, causing the interface to be loaded while it's being transformed, which
      // leads to duplicate class definition error after the interface is transformed and the
      // triggering class loader tries to load it.
      return named("org.apache.logging.log4j.core.impl.ThreadContextDataInjector");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      // Nothing to transform, this type instrumentation is only used for injecting resources.
    }
  }
}
