/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.proxy;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.InvocationHandler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ProxyInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.reflect.Proxy");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("newProxyInstance"))
            .and(takesArguments(3))
            .and(takesArgument(0, ClassLoader.class))
            .and(takesArgument(1, Class[].class))
            .and(takesArgument(2, InvocationHandler.class))
            .and(isPublic())
            .and(isStatic()),
        ProxyInstrumentation.class.getName() + "$FilterDuplicateMarkerInterfaces");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("getProxyClass"))
            .and(takesArguments(2))
            .and(takesArgument(0, ClassLoader.class))
            .and(takesArgument(1, Class[].class))
            .and(isPublic())
            .and(isStatic()),
        ProxyInstrumentation.class.getName() + "$FilterDuplicateMarkerInterfaces");
  }

  public static class FilterDuplicateMarkerInterfaces {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 1, readOnly = false) Class<?>[] interfaces) {
      interfaces = ProxyHelper.filtered(interfaces);
    }
  }
}
