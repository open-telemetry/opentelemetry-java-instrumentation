/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Instant;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments {@code GrpcRequestHandlerMapping.getRequestHandler()} to capture unknown service
 * spans for the Dubbo Triple protocol (gRPC over HTTP/2).
 *
 * <p>Unlike the Dubbo binary protocol where trace context is embedded in the message body
 * (attachments), Triple transmits trace context as HTTP/2 headers. This means the parent trace
 * context is always available even when service routing fails, resulting in a single trace (1
 * client span + 1 server _OTHER span).
 */
public class GrpcRequestHandlerMappingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.rpc.protocol.tri.h12.grpc.GrpcRequestHandlerMapping");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getRequestHandler").and(takesArguments(3)),
        getClass().getName() + "$GetRequestHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetRequestHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Nullable
    public static Timer onEnter() {
      if (!DubboUnknownServiceHelper.isEnabled()) {
        return null;
      }
      return Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(1) Object requestObj,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable Timer timer) {
      if (throwable == null || timer == null) {
        return;
      }

      Instant startTime = timer.startTime();
      Instant endTime = timer.now();
      DubboUnknownServiceHelper.createUnknownServiceSpanFromTriple(
          requestObj, throwable, startTime, endTime);
    }
  }
}
