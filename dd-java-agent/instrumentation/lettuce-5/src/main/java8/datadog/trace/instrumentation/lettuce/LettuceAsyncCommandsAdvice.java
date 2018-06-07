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

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(0) final RedisCommand command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);

    final Scope scope =
        GlobalTracer.get()
            .buildSpan(LettuceInstrumentationUtil.SERVICE_NAME + ".query")
            .startActive(LettuceInstrumentationUtil.doFinishSpanEarly(commandName));

    final Span span = scope.span();
    Tags.DB_TYPE.set(span, LettuceInstrumentationUtil.SERVICE_NAME);
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(span, LettuceInstrumentationUtil.COMPONENT_NAME);

    span.setTag(
        DDTags.RESOURCE_NAME, LettuceInstrumentationUtil.getCommandResourceName(commandName));
    span.setTag(DDTags.SERVICE_NAME, LettuceInstrumentationUtil.SERVICE_NAME);
    span.setTag(DDTags.SPAN_TYPE, LettuceInstrumentationUtil.SERVICE_NAME);

    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {

    final Span span = scope.span();
    if (throwable != null) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap("error.object", throwable));
      span.finish();
      scope.close();
      return;
    }

    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    // close spans on error or normal completion
    if (!LettuceInstrumentationUtil.doFinishSpanEarly(commandName)) {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(scope.span()));
    }
    scope.close();
  }
}
