/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class FilterChainContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.filterchain.FilterChainContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("resume").and(takesArguments(0)),
        FilterChainContextInstrumentation.class.getName() + "$ResumeAdvice");
    transformer.applyAdviceToMethod(
        named("write"), FilterChainContextInstrumentation.class.getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      return Java8BytecodeBridge.rootContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CallDepth onEnter() {
      CallDepth callDepth = CallDepth.forClass(FilterChainContext.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This FilterChainContext filterChainContext, @Advice.Enter CallDepth callDepth) {
      // When exiting the outermost call to write clear context & request from filter chain context.
      // Write makes a copy of the current filter chain context and passes it on. In older versions
      // new and old filter chain context share the attributes, but in newer versions the attributes
      // are also copied. We need to remove the attributes here to ensure that the next request
      // starts with clean state, failing to do so causes http pipelining test to fail with the
      // latest deps.
      if (callDepth.decrementAndGet() == 0) {
        GrizzlyStateStorage.removeContext(filterChainContext);
        GrizzlyStateStorage.removeRequest(filterChainContext);
      }
    }
  }
}
