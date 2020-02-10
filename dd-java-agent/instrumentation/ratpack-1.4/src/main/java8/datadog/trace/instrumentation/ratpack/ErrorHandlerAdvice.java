package datadog.trace.instrumentation.ratpack;

import static datadog.trace.instrumentation.ratpack.RatpackServerDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import ratpack.handling.Context;

public class ErrorHandlerAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void captureThrowable(
      @Advice.Argument(0) final Context ctx, @Advice.Argument(1) final Throwable throwable) {
    final Optional<AgentSpan> span = ctx.maybeGet(AgentSpan.class);
    if (span.isPresent()) {
      DECORATE.onError(span.get(), throwable);
    }
  }
}
