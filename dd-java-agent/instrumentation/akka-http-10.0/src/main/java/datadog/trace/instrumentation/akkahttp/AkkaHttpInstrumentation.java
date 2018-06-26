package datadog.trace.instrumentation.akkahttp;

import static net.bytebuddy.matcher.ElementMatchers.*;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.*;
import akka.stream.stage.*;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.*;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

@Slf4j
@AutoService(Instrumenter.class)
public final class AkkaHttpInstrumentation extends Instrumenter.Configurable {
  public AkkaHttpInstrumentation() {
    super("akka-http", "akka-http-server");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  private static final HelperInjector akkaHttpHelperInjector =
      new HelperInjector(
          AkkaHttpInstrumentation.class.getName() + "$DatadogSyncWrapper",
          AkkaHttpInstrumentation.class.getName() + "$DatadogAsyncWrapper",
          AkkaHttpInstrumentation.class.getName() + "$DatadogAsyncWrapper$1",
          AkkaHttpInstrumentation.class.getName() + "$DatadogAsyncWrapper$2",
          AkkaHttpInstrumentation.class.getName() + "$AkkaHttpHeaders");

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("akka.http.scaladsl.HttpExt"))
        .transform(DDTransformers.defaultTransformers())
        .transform(akkaHttpHelperInjector)
        // Insturmenting akka-streams bindAndHandle api was previously attempted.
        // This proved difficult as there was no clean way to close the async scope
        // in the graph logic after the user's requst handler completes.
        //
        // Instead, we're instrumenting the bindAndHandle function helpers by
        // wrapping the scala functions with our own handlers.
        .transform(
            DDAdvice.create()
                .advice(
                    named("bindAndHandleSync").and(takesArgument(0, named("scala.Function1"))),
                    AkkaHttpSyncAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
                    AkkaHttpAsyncAdvice.class.getName()))
        .asDecorator();
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
        @Advice.Argument(value = 7) Materializer materializer) {
      handler = new DatadogAsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class DatadogSyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public DatadogSyncWrapper(Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
      final Scope scope = DatadogSyncWrapper.createSpan(request);
      try {
        final HttpResponse response = userHandler.apply(request);
        scope.close();
        finishSpan(scope.span(), response);
        return response;
      } catch (Throwable t) {
        scope.close();
        finishSpan(scope.span(), t);
        throw t;
      }
    }

    public static Scope createSpan(HttpRequest request) {
      final SpanContext extractedContext =
          GlobalTracer.get().extract(Format.Builtin.HTTP_HEADERS, new AkkaHttpHeaders(request));
      final Scope scope =
          GlobalTracer.get()
              .buildSpan("akka-http.request")
              .asChildOf(extractedContext)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(Tags.HTTP_METHOD.getKey(), request.method().value())
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag(Tags.COMPONENT.getKey(), "akka-http-server")
              .withTag(Tags.HTTP_URL.getKey(), request.getUri().toString())
              .startActive(false);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      return scope;
    }

    public static void finishSpan(Span span, HttpResponse response) {
      Tags.HTTP_STATUS.set(span, response.status().intValue());

      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
      span.finish();
    }

    public static void finishSpan(Span span, Throwable t) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap("error.object", t));
      Tags.HTTP_STATUS.set(span, 500);

      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
      span.finish();
    }
  }

  public static class DatadogAsyncWrapper
      extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {
    private final Function1<HttpRequest, Future<HttpResponse>> userHandler;
    private final ExecutionContext executionContext;

    public DatadogAsyncWrapper(
        Function1<HttpRequest, Future<HttpResponse>> userHandler,
        ExecutionContext executionContext) {
      this.userHandler = userHandler;
      this.executionContext = executionContext;
    }

    @Override
    public Future<HttpResponse> apply(HttpRequest request) {
      final Scope scope = DatadogSyncWrapper.createSpan(request);
      Future<HttpResponse> futureResponse = null;
      try {
        futureResponse = userHandler.apply(request);
      } catch (Throwable t) {
        scope.close();
        DatadogSyncWrapper.finishSpan(scope.span(), t);
        throw t;
      }
      final Future<HttpResponse> wrapped =
          futureResponse.transform(
              new AbstractFunction1<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(HttpResponse response) {
                  DatadogSyncWrapper.finishSpan(scope.span(), response);
                  return response;
                }
              },
              new AbstractFunction1<Throwable, Throwable>() {
                @Override
                public Throwable apply(Throwable t) {
                  DatadogSyncWrapper.finishSpan(scope.span(), t);
                  return t;
                }
              },
              executionContext);
      scope.close();
      return wrapped;
    }
  }

  public static class AkkaHttpHeaders implements TextMap {
    private final HttpRequest request;

    public AkkaHttpHeaders(HttpRequest request) {
      this.request = request;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      final Map<String, String> javaMap = new HashMap<>(request.headers().size());

      for (HttpHeader header : request.getHeaders()) {
        javaMap.put(header.name(), header.value());
      }

      return javaMap.entrySet().iterator();
    }

    @Override
    public void put(String s, String s1) {
      throw new IllegalStateException("akka http headers can only be extracted");
    }
  }
}
