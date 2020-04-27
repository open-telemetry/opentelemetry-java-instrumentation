package datadog.trace.instrumentation.lettuce;

import static com.lambdaworks.redis.protocol.CommandKeyword.SEGFAULT;
import static com.lambdaworks.redis.protocol.CommandType.CLIENT;
import static com.lambdaworks.redis.protocol.CommandType.CLUSTER;
import static com.lambdaworks.redis.protocol.CommandType.COMMAND;
import static com.lambdaworks.redis.protocol.CommandType.CONFIG;
import static com.lambdaworks.redis.protocol.CommandType.DEBUG;
import static com.lambdaworks.redis.protocol.CommandType.SCRIPT;
import static com.lambdaworks.redis.protocol.CommandType.SHUTDOWN;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.ProtocolKeyword;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class InstrumentationPoints {

  private static final Set<CommandType> NON_INSTRUMENTING_COMMANDS = EnumSet.of(SHUTDOWN, DEBUG);

  private static final Set<CommandType> AGENT_CRASHING_COMMANDS =
    EnumSet.of(CLIENT, CLUSTER, COMMAND, CONFIG, DEBUG, SCRIPT);

  public static final String AGENT_CRASHING_COMMAND_PREFIX = "COMMAND-NAME:";

  public static AgentScope beforeCommand(RedisCommand<?, ?, ?> command) {
    AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    return activateSpan(span, finishSpanEarly(command));
  }

  public static void afterCommand(RedisCommand<?, ?, ?> command,
                                  AgentScope scope,
                                  Throwable throwable,
                                  AsyncCommand<?, ?, ?> asyncCommand) {
      AgentSpan span = scope.span();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      } else if (!finishSpanEarly(command)) {
        asyncCommand.handleAsync((value, ex) -> {
          if (ex instanceof CancellationException) {
            span.setTag("db.command.cancelled", true);
          } else {
            DECORATE.onError(span, ex);
          }
          DECORATE.beforeFinish(span);
          span.finish();
          return null;
        });
      }
      scope.close();
  }

  public static AgentScope beforeConnect(RedisURI redisURI) {
    AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return activateSpan(span, false);
  }

  public static void afterConnect(AgentScope scope, Throwable throwable) {
    AgentSpan span = scope.span();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
    }
    span.finish();
    scope.close();
  }

  /**
   * Determines whether a redis command should finish its relevant span early (as soon as tags are
   * added and the command is executed) because these commands have no return values/call backs, so
   * we must close the span early in order to provide info for the users
   *
   * @param command
   * @return true if finish the span early (the command will not have a return value)
   */
  public static boolean finishSpanEarly(RedisCommand<?, ?, ?> command) {
    ProtocolKeyword keyword = command.getType();
    return isNonInstrumentingCommand(keyword) || isNonInstrumentingKeyword(keyword);
  }

  private static boolean isNonInstrumentingCommand(ProtocolKeyword keyword) {
    return keyword instanceof CommandType && NON_INSTRUMENTING_COMMANDS.contains(keyword);
  }

  private static boolean isNonInstrumentingKeyword(ProtocolKeyword keyword) {
    return keyword == SEGFAULT;
  }

  // Workaround to keep trace agent from crashing
  // Currently the commands in AGENT_CRASHING_COMMANDS_WORDS will crash the trace agent and
  // traces with these commands as the resource name will not be processed by the trace agent
  // https://github.com/DataDog/datadog-trace-agent/blob/master/quantizer/redis.go#L18 has
  // list of commands that will currently fail at the trace agent level.

  /**
   * Workaround to keep trace agent from crashing Currently the commands in
   * AGENT_CRASHING_COMMANDS_WORDS will crash the trace agent and traces with these commands as the
   * resource name will not be processed by the trace agent
   * https://github.com/DataDog/datadog-trace-agent/blob/master/quantizer/redis.go#L18 has list of
   * commands that will currently fail at the trace agent level.
   *
   * @param keyword the actual redis command
   * @return the redis command with a prefix if it is a command that will crash the trace agent,
   *     otherwise, the original command is returned.
   */
  public static String getCommandResourceName(ProtocolKeyword keyword) {
    if (keyword instanceof CommandType && AGENT_CRASHING_COMMANDS.contains(keyword)) {
      return AGENT_CRASHING_COMMAND_PREFIX + keyword.name();
    }
    return keyword.name();
  }
}
