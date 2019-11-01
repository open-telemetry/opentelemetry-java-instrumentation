package datadog.trace.instrumentation.playws;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.playws.HeadersInjectAdapter.SETTER;
import static datadog.trace.instrumentation.playws.PlayWSClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;

@AutoService(Instrumenter.class)
public class PlayWSClientInstrumentation extends Instrumenter.Default {
  public PlayWSClientInstrumentation() {
    super("play-ws");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // CachingAsyncHttpClient rejects overrides to AsyncHandler
    // It also delegates to another AsyncHttpClient
    return safeHasSuperType(named("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient"))
        .and(not(named("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("play.shaded.ahc.org.asynchttpclient.Request")))
            .and(takesArgument(1, named("play.shaded.ahc.org.asynchttpclient.AsyncHandler"))),
        ClientAdvice.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".PlayWSClientDecorator",
      packageName + ".HeadersInjectAdapter",
      packageName + ".AsyncHandlerWrapper"
    };
  }

  public static class ClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentSpan methodEnter(
        @Advice.Argument(0) final Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler) {

      final AgentSpan span = startSpan("play-ws.request");

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      propagate().inject(span, request, SETTER);

      asyncHandler = new AsyncHandlerWrapper(asyncHandler, span);

      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentSpan clientSpan, @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {
        DECORATE.onError(clientSpan, throwable);
        DECORATE.beforeFinish(clientSpan);
        clientSpan.finish();
      }
    }
  }
}
