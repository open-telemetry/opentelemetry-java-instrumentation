/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.v2_13_2;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryContextDataProvider;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class Log4j2InstrumentationModule extends InstrumentationModule {
  public Log4j2InstrumentationModule() {
    super("log4j", "log4j-2.13.2");
  }

  @Override
  public String[] helperResourceNames() {
    return new String[] {
      "META-INF/services/org.apache.logging.log4j.core.util.ContextDataProvider",
    };
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryContextDataProvider"
    };
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.logging.log4j.core.util.ContextDataProvider");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    // have to return at least 1 type instrumentation so that helpers get injected
    return singletonList(new EmptyTypeInstrumentation());
  }

  private static final class EmptyTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      // we cannot use ContextDataProvider here because one of the classes that we inject implements
      // this interface, causing the interface to be loaded while it's being transformed, which
      // leads
      // to duplicate class definition error after the interface is transformed and the triggering
      // class loader tries to load it.
      return named("org.apache.logging.log4j.core.impl.ThreadContextDataInjector");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // Nothing to instrument, no methods to match
      return singletonMap(none(), getClass().getName() + "$MuzzleCheckAdvice");
    }

    // This way muzzle will collect OpenTelemetryContextDataProvider references
    public static class MuzzleCheckAdvice {
      @Advice.OnMethodEnter
      public static void onEnter() {}

      public static void muzzleCheck(OpenTelemetryContextDataProvider contextDataProvider) {
        contextDataProvider.supplyContextData();
      }
    }
  }
}
