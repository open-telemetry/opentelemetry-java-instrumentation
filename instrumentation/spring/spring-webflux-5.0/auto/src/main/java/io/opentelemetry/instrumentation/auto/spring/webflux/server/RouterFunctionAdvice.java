/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.webflux.server;

import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * This advice is responsible for setting additional span parameters for routes implemented with
 * functional interface.
 */
public class RouterFunctionAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.This RouterFunction thiz,
      @Advice.Argument(0) ServerRequest serverRequest,
      @Advice.Return(readOnly = false) Mono<HandlerFunction<?>> result,
      @Advice.Thrown Throwable throwable) {
    if (throwable == null) {
      result = result.doOnSuccessOrError(new RouteOnSuccessOrError(thiz, serverRequest));
    } else {
      AdviceUtils.finishSpanIfPresent(serverRequest, throwable);
    }
  }
}
