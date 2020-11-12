/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpServerTracer.tracer;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

@AutoService(InstrumentationModule.class)
public final class AkkaHttpServerInstrumentationModule extends InstrumentationModule {
  public AkkaHttpServerInstrumentationModule() {
    super("akka-http", "akka-http-server");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AkkaHttpServerInstrumentationModule.class.getName() + "$SyncWrapper",
      AkkaHttpServerInstrumentationModule.class.getName() + "$AsyncWrapper",
      AkkaHttpServerInstrumentationModule.class.getName() + "$AsyncWrapper$1",
      AkkaHttpServerInstrumentationModule.class.getName() + "$AsyncWrapper$2",
      packageName + ".AkkaHttpServerHeaders",
      packageName + ".AkkaHttpServerTracer",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpExtInstrumentation());
  }

  private static final class HttpExtInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("akka.http.scaladsl.HttpExt");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // Instrumenting akka-streams bindAndHandle api was previously attempted.
      // This proved difficult as there was no clean way to close the async scope
      // in the graph logic after the user's request handler completes.
      //
      // Instead, we're instrumenting the bindAndHandle function helpers by
      // wrapping the scala functions with our own handlers.
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          named("bindAndHandleSync").and(takesArgument(0, named("scala.Function1"))),
          AkkaHttpServerInstrumentationModule.class.getName() + "$AkkaHttpSyncAdvice");
      transformers.put(
          named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
          AkkaHttpServerInstrumentationModule.class.getName() + "$AkkaHttpAsyncAdvice");
      return transformers;
    }
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
        @Advice.Argument(7) Materializer materializer) {
      handler = new AsyncWrapper(handler, materializer.executionContext());
    }
  }

  public static class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public SyncWrapper(Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
      Context ctx = tracer().startSpan(request, request, "akka.request");
      Span span = Java8BytecodeBridge.spanFromContext(ctx);
      try (Scope ignored = tracer().startScope(span, null)) {
        HttpResponse response = userHandler.apply(request);
        tracer().end(span, response);
        return response;
      } catch (Throwable t) {
        tracer().endExceptionally(span, t);
        throw t;
      }
    }
  }

  public static class AsyncWrapper extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {
    private final Function1<HttpRequest, Future<HttpResponse>> userHandler;
    private final ExecutionContext executionContext;

    public AsyncWrapper(
        Function1<HttpRequest, Future<HttpResponse>> userHandler,
        ExecutionContext executionContext) {
      this.userHandler = userHandler;
      this.executionContext = executionContext;
    }

    @Override
    public Future<HttpResponse> apply(HttpRequest request) {
      Context ctx = tracer().startSpan(request, request, "akka.request");
      Span span = Java8BytecodeBridge.spanFromContext(ctx);
      try (Scope ignored = tracer().startScope(span, null)) {
        return userHandler
            .apply(request)
            .transform(
                new AbstractFunction1<HttpResponse, HttpResponse>() {
                  @Override
                  public HttpResponse apply(HttpResponse response) {
                    tracer().end(span, response);
                    return response;
                  }
                },
                new AbstractFunction1<Throwable, Throwable>() {
                  @Override
                  public Throwable apply(Throwable t) {
                    tracer().endExceptionally(span, t);
                    return t;
                  }
                },
                executionContext);
      } catch (Throwable t) {
        tracer().endExceptionally(span, t);
        throw t;
      }
    }
  }
}
