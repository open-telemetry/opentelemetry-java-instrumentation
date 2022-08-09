/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class FilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.glassfish.grizzly.filterchain.BaseFilter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(named("org.glassfish.grizzly.filterchain.BaseFilter"))
        // HttpCodecFilter is instrumented in the server instrumentation
        .and(not(ElementMatchers.named("org.glassfish.grizzly.http.HttpCodecFilter")))
        .and(not(ElementMatchers.named("org.glassfish.grizzly.http.HttpServerFilter")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleRead")
            .and(takesArgument(0, named("org.glassfish.grizzly.filterchain.FilterChainContext")))
            .and(isPublic()),
        FilterInstrumentation.class.getName() + "$HandleReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleReadAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This BaseFilter it,
        @Advice.Argument(0) FilterChainContext ctx,
        @Advice.Local("otelScope") Scope scope) {
      if (Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
        return;
      }

      Context context = GrizzlyStateStorage.getContext(ctx);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This BaseFilter it, @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
