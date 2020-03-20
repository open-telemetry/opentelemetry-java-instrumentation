/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.akkahttp;

import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerHeaders.GETTER;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
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
        @Advice.Argument(7) final Materializer materializer) {
      handler = new AsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class WrapperHelper {
    public static SpanWithScope createSpan(final HttpRequest request) {
      final Span.Builder spanBuilder =
          TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(SERVER);
      final SpanContext extractedContext = TRACER.getHttpTextFormat().extract(request, GETTER);
      if (extractedContext.isValid()) {
        spanBuilder.setParent(extractedContext);
      } else {
        // explicitly setting "no parent" in case a span was propagated to this thread
        // by the java-concurrent instrumentation when the thread was started
        spanBuilder.setNoParent();
      }
      final Span span = spanBuilder.startSpan();

      DECORATE.afterStart(span);
      DECORATE.onConnection(span, request);
      DECORATE.onRequest(span, request);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    public static void finishSpan(final Span span, final HttpResponse response) {
      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);

      span.end();
    }

    public static void finishSpan(final Span span, final Throwable t) {
      DECORATE.onError(span, t);
      span.setAttribute(Tags.HTTP_STATUS, 500);
      span.setStatus(Status.UNKNOWN);
      DECORATE.beforeFinish(span);

      span.end();
    }
  }

  @Slf4j
  public static class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public SyncWrapper(final Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(final HttpRequest request) {
      final SpanWithScope spanWithScope = WrapperHelper.createSpan(request);
      final Span span = spanWithScope.getSpan();
      try {
        final HttpResponse response = userHandler.apply(request);
        spanWithScope.closeScope();
        WrapperHelper.finishSpan(span, response);
        return response;
      } catch (final Throwable t) {
        spanWithScope.closeScope();
        WrapperHelper.finishSpan(span, t);
        throw t;
      }
    }
  }

  @Slf4j
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
      final SpanWithScope spanWithScope = WrapperHelper.createSpan(request);
      final Span span = spanWithScope.getSpan();
      Future<HttpResponse> futureResponse = null;
      try {
        futureResponse = userHandler.apply(request);
      } catch (final Throwable t) {
        spanWithScope.closeScope();
        WrapperHelper.finishSpan(span, t);
        throw t;
      }
      final Future<HttpResponse> wrapped =
          futureResponse.transform(
              new AbstractFunction1<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(final HttpResponse response) {
                  WrapperHelper.finishSpan(span, response);
                  return response;
                }
              },
              new AbstractFunction1<Throwable, Throwable>() {
                @Override
                public Throwable apply(final Throwable t) {
                  WrapperHelper.finishSpan(span, t);
                  return t;
                }
              },
              executionContext);
      spanWithScope.closeScope();
      return wrapped;
    }
  }
}
