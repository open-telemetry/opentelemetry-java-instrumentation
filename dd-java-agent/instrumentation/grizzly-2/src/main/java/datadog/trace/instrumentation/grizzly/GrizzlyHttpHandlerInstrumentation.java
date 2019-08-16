package datadog.trace.instrumentation.grizzly;

import static datadog.trace.instrumentation.grizzly.GrizzlyDecorator.DECORATE;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.http.server.AfterServiceListener;
import org.glassfish.grizzly.http.server.Request;

@AutoService(Instrumenter.class)
public class GrizzlyHttpHandlerInstrumentation extends Instrumenter.Default {

  public GrizzlyHttpHandlerInstrumentation() {
    super("grizzly");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.server.HttpHandler");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".GrizzlyDecorator",
      packageName + ".GrizzlyRequestExtractAdapter",
      packageName + ".GrizzlyRequestExtractAdapter$MultivaluedMapFlatIterator",
      getClass().getName() + "$SpanClosingListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("doHandle"))
            .and(takesArgument(0, named("org.glassfish.grizzly.http.server.Request")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.server.Response"))),
        HandleAdvice.class.getName());
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(@Advice.Argument(0) final Request request) {
      if (request.getAttribute(SpanClosingListener.GRIZZLY_SPAN_SPAN) != null) {
        return null;
      }

      final Tracer tracer = GlobalTracer.get();
      final SpanContext parentContext =
          tracer.extract(TEXT_MAP, new GrizzlyRequestExtractAdapter(request));
      final Span span =
          tracer.buildSpan("grizzly.request").ignoreActiveSpan().asChildOf(parentContext).start();
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, request);
      DECORATE.onRequest(span, request);

      final Scope scope = tracer.scopeManager().activate(span, false);
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      request.setAttribute(SpanClosingListener.GRIZZLY_SPAN_SPAN, span);
      request.addAfterServiceListener(SpanClosingListener.LISTENER);

      return scope;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }

      if (throwable != null) {
        final Span span = scope.span();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      }
      scope.close();
    }
  }

  public static class SpanClosingListener implements AfterServiceListener {
    public static final String GRIZZLY_SPAN_SPAN = "datadog.grizzly.span";
    public static final SpanClosingListener LISTENER = new SpanClosingListener();

    @Override
    public void onAfterService(final Request request) {
      final Object spanAttr = request.getAttribute(GRIZZLY_SPAN_SPAN);
      if (spanAttr instanceof Span) {
        request.removeAttribute(GRIZZLY_SPAN_SPAN);
        final Span span = (Span) spanAttr;
        DECORATE.onResponse(span, request.getResponse());
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
