package datadog.trace.instrumentation.lettuce;

import datadog.trace.api.DDTags;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class RedisAsyncCommandsAdvice {

  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME + "-client";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(0) final RedisCommand command) {
    final Scope scope = GlobalTracer.get().buildSpan(SERVICE_NAME + ".query").startActive(false);

    final Span span = scope.span();
    Tags.DB_TYPE.set(span, SERVICE_NAME);
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(span, COMPONENT_NAME);

    String commandName = "Redis Command";
    String commandArgs = null;
    if (command != null) {
      // get the arguments passed into the redis command
      if (command.getArgs() != null) {
        commandArgs = command.getArgs().toCommandString();
      }
      // get the redis command name (i.e. GET, SET, HMSET, etc)
      if (command.getType() != null) {
        commandName = command.getType().name();
        // if it is an AUTH command, then remove the extracted command arguments since it is the password
        if ("AUTH".equals(commandName)) {
          commandArgs = null;
        }
      }
    }

    span.setTag(DDTags.RESOURCE_NAME, commandName);
    span.setTag("db.command.args", commandArgs);
    span.setTag(DDTags.SERVICE_NAME, SERVICE_NAME);
    span.setTag(DDTags.SPAN_TYPE, SERVICE_NAME);

    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {

    if (throwable != null) {
      final Span span = scope.span();
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap("error.object", throwable));
      span.finish();
      scope.close();
      return;
    }

    // close spans on error or normal completion
    asyncCommand.handleAsync(new RedisAsyncBiFunction<>(scope.span()));
    scope.close();
  }
}
