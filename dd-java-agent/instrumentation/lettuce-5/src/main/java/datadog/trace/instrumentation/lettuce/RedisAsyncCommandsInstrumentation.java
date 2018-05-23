package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class RedisAsyncCommandsInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_COMMANDS_HELPERS =
      new HelperInjector(
          RedisAsyncCommandsInstrumentation.class.getName() + "$RedisAsyncConsumer",
          RedisAsyncCommandsInstrumentation.class.getName() + "$RedisAsyncExceptionFunction");
  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME + "-client";

  public RedisAsyncCommandsInstrumentation() {
    super(SERVICE_NAME);
  }

  @Override
  protected AgentBuilder apply(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("io.lettuce.core.AbstractRedisAsyncCommands"))
        .transform(REDIS_ASYNC_COMMANDS_HELPERS)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(named("dispatch"))
                        .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
                    RedisAsyncCommandsAdvice.class.getName()))
        .asDecorator();
  }

  public static class RedisAsyncCommandsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(0) final RedisCommand command) {
      final Scope scope = GlobalTracer.get().buildSpan(SERVICE_NAME + ".query").startActive(false);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, SERVICE_NAME);
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, COMPONENT_NAME);

      String commandName = "Unknown Redis Command";
      String commandArgs = null;
      if (command != null) {
        // get the redis command name (i.e. GET, SET, HMSET, etc)
        if (command.getType() != null) {
          commandName = command.getType().name();
        }
        // get the arguments passed into the redis command
        if (command.getArgs() != null) {
          commandArgs = command.getArgs().toCommandString();
        }
      }

      span.setTag(DDTags.RESOURCE_NAME, commandName);
      span.setTag("db.redis.command.args", commandArgs);
      span.setTag(DDTags.SERVICE_NAME, SERVICE_NAME);
      span.setTag(DDTags.SPAN_TYPE, SERVICE_NAME);

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(readOnly = false) final AsyncCommand<?, ?, ?> asyncCommand) {

      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap("error.object", throwable));
        scope.close();
        return;
      }

      // use lettuce's callback interface to ensure no logic is lost
      asyncCommand.onComplete(new RedisAsyncConsumer<>(scope.span()));
      // exception callbacks are not part of lettuce's api, so use CompletableFuture's, in the future if
      // lettuce does add some sort of exception callback handler, we should use that instead.
      asyncCommand.exceptionally(new RedisAsyncExceptionFunction<>(scope.span()));
      scope.close();
    }
  }

  /**
   * Callback class to close the span on an error in the RedisFuture returned by the lettuce async
   * API
   *
   * @param <Throwable> the error
   * @param <R> the object to return as a result of this callback, should be null as we are only
   *     closing the span and logging the error
   */
  public static class RedisAsyncExceptionFunction<Throwable, R extends Object>
      implements Function<Throwable, R> {
    private final Span span;

    public RedisAsyncExceptionFunction(Span span) {
      this.span = span;
    }

    @Override
    public R apply(Throwable throwable) {
      Tags.ERROR.set(this.span, true);
      this.span.log(Collections.singletonMap("error.object", throwable));
      this.span.finish();
      return null;
    }
  }

  /**
   * Callback class to close the span on a normal execution of the RedisFuture returned by the
   * lettice async API
   *
   * @param <T> the object (async result) from lettuce async API computation
   */
  public static class RedisAsyncConsumer<T> implements Consumer<T> {
    private final Span span;

    public RedisAsyncConsumer(Span span) {
      this.span = span;
    }

    @Override
    public void accept(Object o) {
      this.span.finish();
    }
  }
}
