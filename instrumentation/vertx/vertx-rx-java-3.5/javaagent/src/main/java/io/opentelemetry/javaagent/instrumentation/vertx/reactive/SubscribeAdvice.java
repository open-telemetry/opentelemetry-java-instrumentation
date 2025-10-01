package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import net.bytebuddy.asm.Advice;
import io.opentelemetry.context.Context;
import io.vertx.core.Vertx;

public class SubscribeAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Object observer) {
    // capture current OTel context
    Context current = Context.current();
    Context storedContext = getStoredVertxContext();
    Context finalOtelContext = ((current!=null&&current!=Context.root())||storedContext == null) ?current : storedContext;
    observer = ContextPreservingWrappers.wrapObserverIfNeeded(observer, finalOtelContext);
  }
  private static Context getStoredVertxContext() {
    try {
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      if (vertxContext != null) {
        return vertxContext.get("otel.context");
      }
    } catch (RuntimeException e) {
//      commenting to trink compiler to not give a warning
    }
    return null;
  }

}
