/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RxJava2InstrumentationModule extends InstrumentationModule {

  public RxJava2InstrumentationModule() {
    super("rxjava2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new PluginInstrumentation());
  }

  public static class PluginInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("io.reactivex.plugins.RxJavaPlugins");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          isTypeInitializer(),
          RxJava2InstrumentationModule.class.getName() + "$RxJavaPluginsAdvice");
    }
  }

  public static class RxJavaPluginsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void postStaticInitializer() {
      TracingAssembly.enable();
    }
  }
}
