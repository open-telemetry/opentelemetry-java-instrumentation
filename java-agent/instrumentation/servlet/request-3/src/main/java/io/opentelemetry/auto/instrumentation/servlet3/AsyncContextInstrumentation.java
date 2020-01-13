package io.opentelemetry.auto.instrumentation.servlet3;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.servlet3.HttpServletRequestInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class AsyncContextInstrumentation extends Instrumenter.Default {

  public AsyncContextInstrumentation() {
    super("servlet", "servlet-3");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".HttpServletRequestInjectAdapter"};
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.AsyncContext")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("dispatch")),
        AsyncContextInstrumentation.class.getName() + "$DispatchAdvice");
  }

  /**
   * When a request is dispatched, we want new request to have propagation headers from its parent
   * request. The parent request's span is later closed by {@code
   * TagSettingAsyncListener#onStartAsync}
   */
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean enter(
        @Advice.This final AsyncContext context, @Advice.AllArguments final Object[] args) {
      final int depth = CallDepthThreadLocalMap.incrementCallDepth(AsyncContext.class);
      if (depth > 0) {
        return false;
      }

      final ServletRequest request = context.getRequest();
      final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
      if (spanAttr instanceof AgentSpan) {
        request.removeAttribute(SPAN_ATTRIBUTE);
        final AgentSpan span = (AgentSpan) spanAttr;
        // Override propagation headers by injecting attributes from the current span
        // into the new request
        if (request instanceof HttpServletRequest) {
          propagate().inject(span, (HttpServletRequest) request, SETTER);
        }
        final String path;
        if (args.length == 1 && args[0] instanceof String) {
          path = (String) args[0];
        } else if (args.length == 2 && args[1] instanceof String) {
          path = (String) args[1];
        } else {
          path = "true";
        }
        span.setAttribute("servlet.dispatch", path);
      }
      return true;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exit(@Advice.Enter final boolean topLevel) {
      if (topLevel) {
        CallDepthThreadLocalMap.reset(AsyncContext.class);
      }
    }
  }
}
