package datadog.trace.instrumentation.lettuce;

import datadog.trace.api.DDTags;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(0) final RedisCommand command) {
    Map<String, String> commandMap = LettuceInstrumentationUtil.getCommandInfo(command);
    String commandName = commandMap.get(LettuceInstrumentationUtil.MAP_KEY_CMD_NAME);
    String commandArgs = commandMap.get(LettuceInstrumentationUtil.MAP_KEY_CMD_ARGS);
    ;

    final Scope scope =
        GlobalTracer.get()
            .buildSpan(LettuceInstrumentationUtil.SERVICE_NAME + ".query")
            .startActive(LettuceInstrumentationUtil.doFinishSpanEarly(commandMap));

    final Span span = scope.span();
    Tags.DB_TYPE.set(span, LettuceInstrumentationUtil.SERVICE_NAME);
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(span, LettuceInstrumentationUtil.COMPONENT_NAME);

    span.setTag(DDTags.RESOURCE_NAME, commandName);
    span.setTag("db.command.args", commandArgs);
    span.setTag(DDTags.SERVICE_NAME, LettuceInstrumentationUtil.SERVICE_NAME);
    span.setTag(DDTags.SPAN_TYPE, LettuceInstrumentationUtil.SERVICE_NAME);

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
    asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(scope.span()));
    scope.close();
  }
}
