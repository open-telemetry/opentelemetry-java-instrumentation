/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rxjava.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RxJavaPluginsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.reactivex.rxjava3.plugins.RxJavaPlugins");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isMethod(), this.getClass().getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    // TODO(anuraaga): Replace with adding a type initializer to RxJavaPlugins
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2685
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void activateOncePerClassloader() {
      TracingAssemblyActivation.activate(RxJavaPlugins.class);
    }
  }
}
