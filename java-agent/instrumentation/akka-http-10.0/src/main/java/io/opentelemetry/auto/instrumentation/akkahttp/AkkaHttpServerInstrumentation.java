package io.opentelemetry.auto.instrumentation.akkahttp;

import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerHeaders.GETTER;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.agent.tooling.Instrumenter;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import java.util.HashMap;
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
      AkkaHttpServerInstrumentation.class.getName() + "$WrapperHelper",
      AkkaHttpServerInstrumentation.class.getName() + "$SyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$AsyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$AsyncWrapper$1",
      AkkaHttpServerInstrumentation.class.getName() + "$AsyncWrapper$2",
      packageName + ".AkkaHttpServerHeaders",
      "io.opentelemetry.auto.agent.decorator.BaseDecorator",
      "io.opentelemetry.auto.agent.decorator.ServerDecorator",
      "io.opentelemetry.auto.agent.decorator.HttpServerDecorator",
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
        AkkaHttpServerInstrumentation.class.getName() + "$AkkaHttpSyncAdvice");
    transformers.put(
        named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
        AkkaHttpServerInstrumentation.class.getName() + "$AkkaHttpAsyncAdvice");
    return transformers;
  }

  public static class AkkaHttpSyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, HttpResponse> handler) {
      handler = new SyncWrapper(handler);
    }
  }

  public static class AkkaHttpAsyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, Future<HttpResponse>> handler,
        @Advice.Argument(value = 7) final Materializer materializer) {
      handler = new AsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class WrapperHelper {
    public static AgentScope createSpan(final HttpRequest request) {
      final AgentSpan.Context extractedContext = propagate().extract(request, GETTER);
      final AgentSpan span = startSpan("akka-http.request", extractedContext);

      DECORATE.afterStart(span);
      DECORATE.onConnection(span, request);
      DECORATE.onRequest(span, request);

      return activateSpan(span, false);
    }

    public static void finishSpan(final AgentSpan span, final HttpResponse response) {
      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);

      span.finish();
    }

    public static void finishSpan(final AgentSpan span, final Throwable t) {
      DECORATE.onError(span, t);
      span.setTag(Tags.HTTP_STATUS, 500);
      span.setError(true);
      DECORATE.beforeFinish(span);

      span.finish();
    }
  }

  public static class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public SyncWrapper(final Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(final HttpRequest request) {
      final AgentScope scope = WrapperHelper.createSpan(request);
      try {
        final HttpResponse response = userHandler.apply(request);
        scope.close();
        WrapperHelper.finishSpan(scope.span(), response);
        return response;
      } catch (final Throwable t) {
        scope.close();
        WrapperHelper.finishSpan(scope.span(), t);
        throw t;
      }
    }
  }

  public static class AsyncWrapper extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {
    private final Function1<HttpRequest, Future<HttpResponse>> userHandler;
    private final ExecutionContext executionContext;

    public AsyncWrapper(
        final Function1<HttpRequest, Future<HttpResponse>> userHandler,
        final ExecutionContext executionContext) {
      this.userHandler = userHandler;
      this.executionContext = executionContext;
    }

    @Override
    public Future<HttpResponse> apply(final HttpRequest request) {
      final AgentScope scope = WrapperHelper.createSpan(request);
      Future<HttpResponse> futureResponse = null;
      try {
        futureResponse = userHandler.apply(request);
      } catch (final Throwable t) {
        scope.close();
        WrapperHelper.finishSpan(scope.span(), t);
        throw t;
      }
      final Future<HttpResponse> wrapped =
          futureResponse.transform(
              new AbstractFunction1<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(final HttpResponse response) {
                  WrapperHelper.finishSpan(scope.span(), response);
                  return response;
                }
              },
              new AbstractFunction1<Throwable, Throwable>() {
                @Override
                public Throwable apply(final Throwable t) {
                  WrapperHelper.finishSpan(scope.span(), t);
                  return t;
                }
              },
              executionContext);
      scope.close();
      return wrapped;
    }
  }
}
