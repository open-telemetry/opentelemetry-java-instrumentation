/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.osgi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.bootstrap.internal.InClassLoaderMatcher;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * The ClassLoaderMatcher's call to ClassLoader.getResource() causes the Eclipse OSGi class loader
 * to "dynamically import" a bundle for the package if such a bundle is not found, which can lead to
 * application failure later on due to the bundle hierarchy no longer being "consistent".
 *
 * <p>Any side-effect of the ClassLoaderMatcher's call to ClassLoader.getResource() is generally
 * undesirable, and so this instrumentation patches the behavior and suppresses the "dynamic import"
 * of the missing package/bundle when the call is originating from ClassLoaderMatcher..
 */
class EclipseOsgiInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.osgi.internal.loader.BundleLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("isDynamicallyImported")).and(returns(boolean.class)),
        this.getClass().getName() + "$IsDynamicallyImportedAdvice");
  }

  @SuppressWarnings("unused")
  public static class IsDynamicallyImportedAdvice {

    // "skipOn" is used to skip execution of the instrumented method when a ClassLoaderMatcher is
    // currently executing, since we will be returning false regardless in onExit below
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter() {
      return InClassLoaderMatcher.get();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(readOnly = false) boolean result,
        @Advice.Enter boolean inClassLoaderMatcher) {
      if (inClassLoaderMatcher) {
        result = false;
      }
    }
  }
}
