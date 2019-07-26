package datadog.trace.agent.test.base;

import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;

public abstract class HttpServerTestAdvice {

  /**
   * This advice should be applied at the root of a http server request to validate the
   * instrumentation correctly ignores other traces.
   */
  public static class ServerEntryAdvice {
    @Advice.OnMethodEnter
    public static Scope methodEnter() {
      if (!HttpServerTest.ENABLE_TEST_ADVICE.get()) {
        // Skip if not running the HttpServerTest.
        return NoopScopeManager.NoopScope.INSTANCE;
      }
      final Tracer tracer = GlobalTracer.get();
      if (tracer.activeSpan() != null) {
        return NoopScopeManager.NoopScope.INSTANCE;
      } else {
        return tracer
            .buildSpan("TEST_SPAN")
            .withTag(DDTags.RESOURCE_NAME, "ServerEntry")
            .startActive(true);
      }
    }

    @Advice.OnMethodExit
    public static void methodExit(@Advice.Enter final Scope scope) {
      scope.close();
    }
  }
}
