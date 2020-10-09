/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;

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
      @Advice.Enter RedisCommand command, @Advice.Return(readOnly = false) Mono<?> publisher) {
    boolean finishSpanOnClose = !expectsResponse(command);
    LettuceMonoDualConsumer mdc = new LettuceMonoDualConsumer(command, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(mdc);
    // register the call back to close the span only if necessary
    if (!finishSpanOnClose) {
      publisher = publisher.doOnSuccessOrError(mdc);
    }
  }
}
