package datadog.trace.instrumentation.akkahttp;

import static datadog.trace.instrumentation.akkahttp.AkkaHttpServerDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

@Slf4j
@AutoService(Instrumenter.class)
public final class AkkaHttpServerInstrumentation extends Instrumenter.Default {
  public AkkaHttpServerInstrumentation() {
    super("akka-http", "akka-http-server");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogWrapperHelper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogSyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper$1",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper$2",
      AkkaHttpServerInstrumentation.class.getName() + "$AkkaHttpServerHeaders",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".AkkaHttpServerDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // Instrumenting akka-streams bindAndHandle api was previously attempted.
    // This proved difficult as there was no clean way to close the async scope
    // in the graph logic after the user's request handler completes.
    //
    // Instead, we're instrumenting the bindAndHandle function helpers by
    // wrapping the scala functions with our own handlers.
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("bindAndHandleSync").and(takesArgument(0, named("scala.Function1"))),
        AkkaHttpSyncAdvice.class.getName());
    transformers.put(
        named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
        AkkaHttpAsyncAdvice.class.getName());
    return transformers;
  }

  public static class AkkaHttpSyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, HttpResponse> handler) {
      handler = new DatadogSyncWrapper(handler);
    }
  }

  public static class AkkaHttpAsyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, scala.concurrent.Future<HttpResponse>> handler,
        @Advice.Argument(value = 7) final Materializer materializer) {
      handler = new DatadogAsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class DatadogWrapperHelper {
    public static Scope createSpan(final HttpRequest request) {
      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new AkkaHttpServerHeaders(request));
      final Scope scope =
          GlobalTracer.get()
              .buildSpan("akka-http.request")
              .asChildOf(extractedContext)
              .startActive(false);

      DECORATE.afterStart(scope.span());
      DECORATE.onConnection(scope.span(), request);
      DECORATE.onRequest(scope.span(), request);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      return scope;
    }

    public static void finishSpan(final Span span, final HttpResponse response) {
      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);

      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
      span.finish();
    }

    public static void finishSpan(final Span span, final Throwable t) {
      DECORATE.onError(span, t);
      Tags.HTTP_STATUS.set(span, 500);
      DECORATE.beforeFinish(span);

      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
      span.finish();
    }
  }

  public static class DatadogSyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public DatadogSyncWrapper(final Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(final HttpRequest request) {
      final Scope scope = DatadogWrapperHelper.createSpan(request);
      try {
        final HttpResponse response = userHandler.apply(request);
        scope.close();
        DatadogWrapperHelper.finishSpan(scope.span(), response);
        return response;
      } catch (final Throwable t) {
        scope.close();
        DatadogWrapperHelper.finishSpan(scope.span(), t);
        throw t;
      }
    }
  }

  public static class DatadogAsyncWrapper
      extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {
    private final Function1<HttpRequest, Future<HttpResponse>> userHandler;
    private final ExecutionContext executionContext;

    public DatadogAsyncWrapper(
        final Function1<HttpRequest, Future<HttpResponse>> userHandler,
        final ExecutionContext executionContext) {
      this.userHandler = userHandler;
      this.executionContext = executionContext;
    }

    @Override
    public Future<HttpResponse> apply(final HttpRequest request) {
      final Scope scope = DatadogWrapperHelper.createSpan(request);
      Future<HttpResponse> futureResponse = null;
      try {
        futureResponse = userHandler.apply(request);
      } catch (final Throwable t) {
        scope.close();
        DatadogWrapperHelper.finishSpan(scope.span(), t);
        throw t;
      }
      final Future<HttpResponse> wrapped =
          futureResponse.transform(
              new AbstractFunction1<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(final HttpResponse response) {
                  DatadogWrapperHelper.finishSpan(scope.span(), response);
                  return response;
                }
              },
              new AbstractFunction1<Throwable, Throwable>() {
                @Override
                public Throwable apply(final Throwable t) {
                  DatadogWrapperHelper.finishSpan(scope.span(), t);
                  return t;
                }
              },
              executionContext);
      scope.close();
      return wrapped;
    }
  }

  public static class AkkaHttpServerHeaders implements TextMap {
    private final HttpRequest request;

    public AkkaHttpServerHeaders(final HttpRequest request) {
      this.request = request;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      final Map<String, String> javaMap = new HashMap<>(request.headers().size());

      for (final HttpHeader header : request.getHeaders()) {
        javaMap.put(header.name(), header.value());
      }

      return javaMap.entrySet().iterator();
    }

    @Override
    public void put(final String name, final String value) {
      throw new IllegalStateException("akka http server headers can only be extracted");
    }
  }
}
