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
package io.opentelemetry.auto.instrumentation.lettuce.rx;

import static io.opentelemetry.auto.instrumentation.lettuce.LettuceInstrumentationUtil.doFinishSpanEarly;

import io.lettuce.core.protocol.RedisCommand;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Flux;

public class LettuceFluxCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static RedisCommand extractCommandName(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    return supplier.get();
  }

  // if there is an exception thrown, then don't make spans
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final RedisCommand command,
      @Advice.Return(readOnly = false) Flux<?> publisher) {

    final boolean finishSpanOnClose = doFinishSpanEarly(command);
    final LettuceFluxTerminationRunnable handler =
        new LettuceFluxTerminationRunnable(command, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(handler.getOnSubscribeConsumer());
    // don't register extra callbacks to finish the spans if the command being instrumented is one
    // of those that return
    // Mono<Void> (In here a flux is created first and then converted to Mono<Void>)
    if (!finishSpanOnClose) {
      publisher = publisher.doOnEach(handler);
      publisher = publisher.doOnCancel(handler);
    }
  }
}
