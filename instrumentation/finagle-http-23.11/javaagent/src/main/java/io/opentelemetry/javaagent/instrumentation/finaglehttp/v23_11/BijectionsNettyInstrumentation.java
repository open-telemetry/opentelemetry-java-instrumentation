/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.finagle.http.Request;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Bridges the Context from Netty request types to finagle request types. */
class BijectionsNettyInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finagle.netty4.http.Bijections$netty$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("fullRequestToFinagle")), getClass().getName() + "$FullRequestAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("chunkedRequestToFinagle")),
        getClass().getName() + "$ChunkedRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class FullRequestAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onApplyExit(
        @Advice.Return(readOnly = false) Request ret, @Advice.Argument(0) FullHttpRequest in) {
      Helpers.chainContextToFinagle(in, ret);
    }
  }

  @SuppressWarnings("unused")
  public static class ChunkedRequestAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onApplyExit(
        @Advice.Return(readOnly = false) Request ret, @Advice.Argument(0) HttpRequest in) {
      Helpers.chainContextToFinagle(in, ret);
    }
  }
}
