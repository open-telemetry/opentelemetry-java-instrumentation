/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerTracer.tracer;
import static java.util.Collections.singletonList;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

@AutoService(InstrumentationModule.class)
public class AkkaHttpServerInstrumentationModule extends InstrumentationModule {
  public AkkaHttpServerInstrumentationModule() {
    super("akka-http", "akka-http-server");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpExtServerInstrumentation());
  }

  public static class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public SyncWrapper(Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
      Context ctx = tracer().startSpan(request, request, null, "akka.request");
      try (Scope ignored = ctx.makeCurrent()) {
        HttpResponse response = userHandler.apply(request);
        tracer().end(ctx, response);
        return response;
      } catch (Throwable t) {
        tracer().endExceptionally(ctx, t);
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
      Context ctx = tracer().startSpan(request, request, null, "akka.request");
      try (Scope ignored = ctx.makeCurrent()) {
        return userHandler
            .apply(request)
            .transform(
                new AbstractFunction1<HttpResponse, HttpResponse>() {
                  @Override
                  public HttpResponse apply(HttpResponse response) {
                    tracer().end(ctx, response);
                    return response;
                  }
                },
                new AbstractFunction1<Throwable, Throwable>() {
                  @Override
                  public Throwable apply(Throwable t) {
                    tracer().endExceptionally(ctx, t);
                    return t;
                  }
                },
                executionContext);
      } catch (Throwable t) {
        tracer().endExceptionally(ctx, t);
        throw t;
      }
    }
  }
}
