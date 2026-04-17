/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.finagle.http.Request;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Bridges the Context from Netty request types to finagle request types. */
class BijectionsFinagleInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finagle.netty4.http.Bijections$finagle$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("requestToNetty")), getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onApplyExit(
        @Advice.Return(readOnly = false) HttpRequest ret, @Advice.Argument(0) Request in) {
      Context context = in.ctx().apply(Helpers.OTEL_CONTEXT_KEY);

      VirtualField.find(HttpRequest.class, Context.class).set(ret, context);
    }
  }
}
