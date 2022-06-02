/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.errorResponse;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.AkkaHttpServerSingletons.instrumenter;
import static java.util.Arrays.asList;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Attributes;
import akka.stream.BidiShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
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
    return asList(
        new HttpExtServerInstrumentation(),
        new AkkaHttpServerSourceInstrumentation(),
        new DispatchersInstrumentation()
    );
  }

  public static class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
    private final Function1<HttpRequest, HttpResponse> userHandler;

    public SyncWrapper(Function1<HttpRequest, HttpResponse> userHandler) {
      this.userHandler = userHandler;
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return userHandler.apply(request);
      }
      Context context = instrumenter().start(parentContext, request);
      try (Scope ignored = context.makeCurrent()) {
        HttpResponse response = userHandler.apply(request);
        instrumenter().end(context, request, response, null);
        return response;
      } catch (Throwable t) {
        instrumenter().end(context, request, errorResponse(), t);
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
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return userHandler.apply(request);
      }
      Context context = instrumenter().start(parentContext, request);
      try (Scope ignored = context.makeCurrent()) {
        return userHandler
            .apply(request)
            .transform(
                new AbstractFunction1<HttpResponse, HttpResponse>() {
                  @Override
                  public HttpResponse apply(HttpResponse response) {
                    instrumenter().end(context, request, response, null);
                    return response;
                  }
                },
                new AbstractFunction1<Throwable, Throwable>() {
                  @Override
                  public Throwable apply(Throwable t) {
                    instrumenter().end(context, request, errorResponse(), t);
                    return t;
                  }
                },
                executionContext);
      } catch (Throwable t) {
        instrumenter().end(context, request, null, t);
        throw t;
      }
    }
  }

  public static class FlowWrapper extends GraphStage<BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest>> {
    private final Inlet<HttpResponse> in1 = Inlet.create("out.in");
    private final Outlet<HttpResponse> out1 = Outlet.create("out.out");
    private final Inlet<HttpRequest> in2 = Inlet.create("in.in");
    private final Outlet<HttpRequest> out2 = Outlet.create("in.out");

    @Override
    public BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape() {
      return BidiShape.of(in1, out1, in2, out2);
    }

    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
      return new FlowSpanLogic(shape());
    }

    //An instance of this class will be created per connection. Only one request will be processed at a time.
    private static class FlowSpanLogic extends GraphStageLogic {
      private Context ctx = null;
      private Scope scope = null;
      private HttpRequest currentRequest = null;

      public FlowSpanLogic(BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape) {
        super(shape);

        setHandler(
            shape.out1(),
            new AbstractOutHandler() {
              @Override
              public void onPull() {
                pull(shape.in1());
              }
            }
        );

        setHandler(
            shape.out2(),
            new AbstractOutHandler() {
              @Override
              public void onPull() {
                pull(shape.in2());
              }
            }
        );

        setHandler(
            shape.in2(),
            new AbstractInHandler() {
              @Override
              public void onPush() {
                HttpRequest request = grab(shape.in2());
                createSpan(request);
                push(shape.out2(), request);
              }

              @Override
              public void onUpstreamFailure(Throwable ex) throws Exception {
                finishSpan(null, ex);
                super.onUpstreamFailure(ex);
              }
            }
        );

        setHandler(
            shape.in1(),
            new AbstractInHandler() {
              @Override
              public void onPush() {
                HttpResponse response = grab(shape.in1());
                finishSpan(response, null);
                push(shape.out1(), response);
              }

              @Override
              public void onUpstreamFailure(Throwable ex) throws Exception {
                finishSpan(null, ex);
                super.onUpstreamFailure(ex);
              }
            }
        );
      }

      private void createSpan(HttpRequest request) {
        Context parentContext = currentContext();

        if (instrumenter().shouldStart(parentContext, request)) {
          ctx = instrumenter().start(parentContext, request);
          scope = ctx.makeCurrent();
          currentRequest = request;
        }
      }

      private void finishSpan(HttpResponse response, Throwable t) {
        if(null != scope) {
          scope.close();

          instrumenter().end(ctx, currentRequest, response, t);

          currentRequest = null;
          scope = null;
          ctx = null;
        }
      }
    }
  }
}
