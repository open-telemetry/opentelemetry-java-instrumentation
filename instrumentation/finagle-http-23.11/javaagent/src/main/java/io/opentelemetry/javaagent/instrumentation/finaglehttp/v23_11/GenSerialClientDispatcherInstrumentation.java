/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.Helpers.OTEL_CONTEXT_KEY;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.finagle.http.Request;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GenSerialClientDispatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.twitter.finagle.dispatch.GenSerialClientDispatcher"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twitter.finagle.dispatch.GenSerialClientDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("dispatch"), getClass().getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void methodEnter(@Advice.Argument(0) Object req) {
      if (req instanceof Request) {
        // practically this will always be a Request, from HttpClientDispatcher
        Request request = (Request) req;
        Context context = Context.current();
        // we don't lock this here bc the request can theoretically be reused
        request.ctx().update(OTEL_CONTEXT_KEY, context);
      }
    }
  }
}
