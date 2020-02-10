package datadog.trace.agent.test.base;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
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
        final AgentSpan span = startSpan("TEST_SPAN").setTag(DDTags.RESOURCE_NAME, "ServerEntry");
        final AgentScope scope = activateSpan(span, true);
        scope.setAsyncPropagation(true);
        return scope;
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
