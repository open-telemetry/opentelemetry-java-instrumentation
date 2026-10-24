/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.RESPONSE_CALLBACK;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.ResponseCallback;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HttpObjectAggregatorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.netty.handler.codec.http.HttpObjectAggregator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("aggregate")
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.netty.handler.codec.http.FullHttpMessage")))
            .and(takesArgument(1, named("io.netty.handler.codec.http.HttpContent"))),
        getClass().getName() + "$AggregateAdvice");
  }

  @SuppressWarnings("unused")
  public static class AggregateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ResponseCallback onEnter(@Advice.Argument(1) HttpContent content) {
      return ClientContexts.currentResponseCallback(content);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) FullHttpMessage aggregated,
        @Advice.Enter @Nullable ResponseCallback callback) {
      if (callback != null) {
        RESPONSE_CALLBACK.set(aggregated, callback);
      }
    }
  }
}
