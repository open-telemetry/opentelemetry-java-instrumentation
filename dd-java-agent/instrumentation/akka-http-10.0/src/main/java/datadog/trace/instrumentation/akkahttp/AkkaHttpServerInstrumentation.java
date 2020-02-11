package datadog.trace.instrumentation.akkahttp;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.akkahttp.AkkaHttpServerDecorator.DECORATE;
import static datadog.trace.instrumentation.akkahttp.AkkaHttpServerHeaders.GETTER;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.context.TraceScope;
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
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogWrapperHelper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogSyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper$1",
      AkkaHttpServerInstrumentation.class.getName() + "$DatadogAsyncWrapper$2",
      packageName + ".AkkaHttpServerHeaders",
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
      handler = new DatadogSyncWrapper(handler);
    }
  }

  public static class AkkaHttpAsyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, Future<HttpResponse>> handler,
        @Advice.Argument(value = 7) final Materializer materializer) {
      handler = new DatadogAsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class DatadogWrapperHelper {
    public static AgentScope createSpan(final HttpRequest request) {
      final AgentSpan.Context extractedContext = propagate().extract(request, GETTER);
      final AgentSpan span = startSpan("akka-http.request", extractedContext);

      DECORATE.afterStart(span);
      DECORATE.onConnection(span, request);
      DECORATE.onRequest(span, request);

      final AgentScope scope = activateSpan(span, false);
      scope.setAsyncPropagation(true);
      return scope;
    }

    public static void finishSpan(final AgentSpan span, final HttpResponse response) {
      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);

      final TraceScope scope = activeScope();
      if (scope != null) {
        scope.setAsyncPropagation(false);
      }
      span.finish();
    }

    public static void finishSpan(final AgentSpan span, final Throwable t) {
      DECORATE.onError(span, t);
      span.setTag(Tags.HTTP_STATUS, 500);
      DECORATE.beforeFinish(span);

      final TraceScope scope = activeScope();
      if (scope != null) {
        scope.setAsyncPropagation(false);
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
      final AgentScope scope = DatadogWrapperHelper.createSpan(request);
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
      final AgentScope scope = DatadogWrapperHelper.createSpan(request);
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
}
