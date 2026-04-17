/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.finagle.http.Request;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Part 3/3 of bridging the otel Context from netty to finagle.
 * Instruments the dispatch call to extract the Context from the finagle Request
 * context and assert it as current for the duration of the dispatch.
 * This allows the other instrumentations to take over and carry the Context
 * to its next span/s.
 */
class GenStreamingServerDispatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.twitter.finagle.http.GenStreamingSerialServerDispatcher"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twitter.finagle.http.GenStreamingSerialServerDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("dispatch"), getClass().getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope methodEnter(@Advice.Argument(0) Object req) {
      if (req instanceof Request) {
        // practically this will always be a Request, from HttpServerDispatcher
        Request request = (Request) req;
        Context context = request.ctx().apply(Helpers.OTEL_CONTEXT_KEY);
        return context != null ? context.makeCurrent() : null;
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void methodExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
