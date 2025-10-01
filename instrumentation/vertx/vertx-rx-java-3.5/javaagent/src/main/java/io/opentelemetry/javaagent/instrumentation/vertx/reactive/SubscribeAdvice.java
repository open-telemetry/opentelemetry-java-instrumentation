package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import net.bytebuddy.asm.Advice;
import io.opentelemetry.context.Context;

public class SubscribeAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Object observer) {
    // capture current OTel context
    Context current = Context.current();
    observer = ContextPreservingWrappers.wrapObserverIfNeeded(observer, current);
  }
}
