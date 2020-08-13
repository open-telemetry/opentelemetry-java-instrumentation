/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.spring.webflux.server;

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
      @Advice.This final RouterFunction thiz,
      @Advice.Argument(0) final ServerRequest serverRequest,
      @Advice.Return(readOnly = false) Mono<HandlerFunction<?>> result,
      @Advice.Thrown final Throwable throwable) {
    if (throwable == null) {
      result = result.doOnSuccessOrError(new RouteOnSuccessOrError(thiz, serverRequest));
    } else {
      AdviceUtils.finishSpanIfPresent(serverRequest, throwable);
    }
  }
}
