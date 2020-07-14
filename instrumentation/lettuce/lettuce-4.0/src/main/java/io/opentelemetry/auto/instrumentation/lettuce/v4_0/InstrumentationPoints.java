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

package io.opentelemetry.auto.instrumentation.lettuce.v4_0;

import static com.lambdaworks.redis.protocol.CommandKeyword.SEGFAULT;
import static com.lambdaworks.redis.protocol.CommandType.DEBUG;
import static com.lambdaworks.redis.protocol.CommandType.SHUTDOWN;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.ProtocolKeyword;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class InstrumentationPoints {

  private static final Set<CommandType> NON_INSTRUMENTING_COMMANDS = EnumSet.of(SHUTDOWN, DEBUG);

  public static SpanWithScope beforeCommand(final RedisCommand<?, ?, ?> command) {
    final Span span = LettuceDatabaseClientTracer.TRACER.startSpan(null, command, null);
    return new SpanWithScope(span, LettuceDatabaseClientTracer.TRACER.startScope(span));
  }

  public static void afterCommand(
      final RedisCommand<?, ?, ?> command,
      final SpanWithScope spanWithScope,
      final Throwable throwable,
      final AsyncCommand<?, ?, ?> asyncCommand) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      LettuceDatabaseClientTracer.TRACER.endExceptionally(span, throwable);
    } else if (expectsResponse(command)) {
      asyncCommand.handleAsync(
          (value, ex) -> {
            if (ex == null) {
              LettuceDatabaseClientTracer.TRACER.end(span);
            } else if (ex instanceof CancellationException) {
              span.setAttribute("db.command.cancelled", true);
              LettuceDatabaseClientTracer.TRACER.end(span);
            } else {
              LettuceDatabaseClientTracer.TRACER.endExceptionally(span, ex);
            }
            return null;
          });
    } else {
      // No response is expected, so we must finish the span now.
      LettuceDatabaseClientTracer.TRACER.end(span);
    }
    spanWithScope.closeScope();
  }

  public static SpanWithScope beforeConnect(final RedisURI redisURI) {
    final Span span =
        LettuceConnectionDatabaseClientTracer.TRACER.startSpan(redisURI, "CONNECT", null);
    return new SpanWithScope(span, LettuceConnectionDatabaseClientTracer.TRACER.startScope(span));
  }

  public static void afterConnect(final SpanWithScope spanWithScope, final Throwable throwable) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      LettuceConnectionDatabaseClientTracer.TRACER.endExceptionally(span, throwable);
    } else {
      LettuceConnectionDatabaseClientTracer.TRACER.end(span);
    }
    spanWithScope.closeScope();
  }

  /**
   * Determines whether a redis command should finish its relevant span early (as soon as tags are
   * added and the command is executed) because these commands have no return values/call backs, so
   * we must close the span early in order to provide info for the users
   *
   * @return false if the span should finish early (the command will not have a return value)
   */
  public static boolean expectsResponse(final RedisCommand<?, ?, ?> command) {
    final ProtocolKeyword keyword = command.getType();
    return !(isNonInstrumentingCommand(keyword) || isNonInstrumentingKeyword(keyword));
  }

  private static boolean isNonInstrumentingCommand(final ProtocolKeyword keyword) {
    return keyword instanceof CommandType && NON_INSTRUMENTING_COMMANDS.contains(keyword);
  }

  private static boolean isNonInstrumentingKeyword(final ProtocolKeyword keyword) {
    return keyword == SEGFAULT;
  }
}
