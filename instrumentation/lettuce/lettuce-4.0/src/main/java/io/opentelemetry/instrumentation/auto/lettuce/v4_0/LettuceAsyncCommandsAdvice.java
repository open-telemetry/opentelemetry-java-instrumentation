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

package io.opentelemetry.instrumentation.auto.lettuce.v4_0;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final RedisCommand<?, ?, ?> command) {
    return InstrumentationPoints.beforeCommand(command);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) final RedisCommand<?, ?, ?> command,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {
    InstrumentationPoints.afterCommand(command, spanWithScope, throwable, asyncCommand);
  }
}
