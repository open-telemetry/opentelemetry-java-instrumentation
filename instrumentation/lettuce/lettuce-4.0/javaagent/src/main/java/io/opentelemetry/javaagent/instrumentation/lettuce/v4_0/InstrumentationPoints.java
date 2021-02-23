/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static com.lambdaworks.redis.protocol.CommandKeyword.SEGFAULT;
import static com.lambdaworks.redis.protocol.CommandType.DEBUG;
import static com.lambdaworks.redis.protocol.CommandType.SHUTDOWN;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceDatabaseClientTracer.tracer;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.ProtocolKeyword;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class InstrumentationPoints {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.lettuce.experimental-span-attributes", false);

  private static final Set<CommandType> NON_INSTRUMENTING_COMMANDS = EnumSet.of(SHUTDOWN, DEBUG);

  public static void afterCommand(
      RedisCommand<?, ?, ?> command,
      Context context,
      Throwable throwable,
      AsyncCommand<?, ?, ?> asyncCommand) {
    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else if (expectsResponse(command)) {
      asyncCommand.handleAsync(
          (value, ex) -> {
            if (ex == null) {
              tracer().end(context);
            } else if (ex instanceof CancellationException) {
              if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
                Span span = Span.fromContext(context);
                span.setAttribute("lettuce.command.cancelled", true);
              }
              tracer().end(context);
            } else {
              tracer().endExceptionally(context, ex);
            }
            return null;
          });
    } else {
      // No response is expected, so we must finish the span now.
      tracer().end(context);
    }
  }

  /**
   * Determines whether a redis command should finish its relevant span early (as soon as tags are
   * added and the command is executed) because these commands have no return values/call backs, so
   * we must close the span early in order to provide info for the users.
   *
   * @return false if the span should finish early (the command will not have a return value)
   */
  public static boolean expectsResponse(RedisCommand<?, ?, ?> command) {
    ProtocolKeyword keyword = command.getType();
    return !(isNonInstrumentingCommand(keyword) || isNonInstrumentingKeyword(keyword));
  }

  private static boolean isNonInstrumentingCommand(ProtocolKeyword keyword) {
    return keyword instanceof CommandType && NON_INSTRUMENTING_COMMANDS.contains(keyword);
  }

  private static boolean isNonInstrumentingKeyword(ProtocolKeyword keyword) {
    return keyword == SEGFAULT;
  }
}
