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
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

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
    final String spanName = command == null ? "Redis Command" : command.getType().name();
    final Span span =
        LettuceClientDecorator.TRACER.spanBuilder(spanName).setSpanKind(CLIENT).startSpan();
    LettuceClientDecorator.DECORATE.afterStart(span);
    return new SpanWithScope(span, currentContextWith(span));
  }

  public static void afterCommand(
      final RedisCommand<?, ?, ?> command,
      final SpanWithScope spanWithScope,
      final Throwable throwable,
      final AsyncCommand<?, ?, ?> asyncCommand) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      LettuceClientDecorator.DECORATE.onError(span, throwable);
      LettuceClientDecorator.DECORATE.beforeFinish(span);
      span.end();
    } else if (finishSpanEarly(command)) {
      span.end();
    } else {
      asyncCommand.handleAsync(
          (value, ex) -> {
            if (ex instanceof CancellationException) {
              span.setAttribute("db.command.cancelled", true);
            } else {
              LettuceClientDecorator.DECORATE.onError(span, ex);
            }
            LettuceClientDecorator.DECORATE.beforeFinish(span);
            span.end();
            return null;
          });
    }
    spanWithScope.closeScope();
    // span may be finished by handleAsync call above.
  }

  public static SpanWithScope beforeConnect(final RedisURI redisURI) {
    final Span span =
        LettuceClientDecorator.TRACER.spanBuilder("CONNECT").setSpanKind(CLIENT).startSpan();
    LettuceClientDecorator.DECORATE.afterStart(span);
    LettuceClientDecorator.DECORATE.onConnection(span, redisURI);
    return new SpanWithScope(span, currentContextWith(span));
  }

  public static void afterConnect(final SpanWithScope spanWithScope, final Throwable throwable) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      LettuceClientDecorator.DECORATE.onError(span, throwable);
      LettuceClientDecorator.DECORATE.beforeFinish(span);
    }
    span.end();
    spanWithScope.closeScope();
  }

  /**
   * Determines whether a redis command should finish its relevant span early (as soon as tags are
   * added and the command is executed) because these commands have no return values/call backs, so
   * we must close the span early in order to provide info for the users
   *
   * @param command
   * @return true if finish the span early (the command will not have a return value)
   */
  public static boolean finishSpanEarly(final RedisCommand<?, ?, ?> command) {
    final ProtocolKeyword keyword = command.getType();
    return isNonInstrumentingCommand(keyword) || isNonInstrumentingKeyword(keyword);
  }

  private static boolean isNonInstrumentingCommand(final ProtocolKeyword keyword) {
    return keyword instanceof CommandType && NON_INSTRUMENTING_COMMANDS.contains(keyword);
  }

  private static boolean isNonInstrumentingKeyword(final ProtocolKeyword keyword) {
    return keyword == SEGFAULT;
  }
}
