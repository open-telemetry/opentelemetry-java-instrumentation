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

package io.opentelemetry.instrumentation.auto.lettuce.v5_0.rx;

import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;

import io.lettuce.core.protocol.RedisCommand;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Mono;

public class LettuceMonoCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static RedisCommand extractCommandName(
      @Advice.Argument(0) Supplier<RedisCommand> supplier) {
    return supplier.get();
  }

  // throwables wouldn't matter here, because no spans have been started due to redis command not
  // being run until the user subscribes to the Mono publisher
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter RedisCommand command,
      @Advice.Return(readOnly = false) Mono<?> publisher) {
    boolean finishSpanOnClose = !expectsResponse(command);
    LettuceMonoDualConsumer mdc = new LettuceMonoDualConsumer(command, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(mdc);
    // register the call back to close the span only if necessary
    if (!finishSpanOnClose) {
      publisher = publisher.doOnSuccessOrError(mdc);
    }
  }
}
