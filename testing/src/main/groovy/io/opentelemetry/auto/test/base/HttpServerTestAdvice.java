package io.opentelemetry.auto.test.base;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public abstract class HttpServerTestAdvice {

  /**
   * This advice should be applied at the root of a http server request to validate the
   * instrumentation correctly ignores other traces.
   */
  public static class ServerEntryAdvice {
    @Advice.OnMethodEnter
    public static AgentScope methodEnter() {
      if (!HttpServerTest.ENABLE_TEST_ADVICE.get()) {
        // Skip if not running the HttpServerTest.
        return null;
      }
      if (activeSpan() != null) {
        return null;
      } else {
        final AgentSpan span =
            startSpan("TEST_SPAN").setAttribute(MoreTags.RESOURCE_NAME, "ServerEntry");
        return activateSpan(span, true);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void methodExit(@Advice.Enter final AgentScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
