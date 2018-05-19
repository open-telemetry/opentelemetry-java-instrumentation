package datadog.trace.instrumentation.akkahttp;

import static net.bytebuddy.matcher.ElementMatchers.*;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.*;
import akka.stream.scaladsl.Flow;
import akka.stream.stage.*;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.*;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
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

@Slf4j
@AutoService(Instrumenter.class)
public final class AkkaHttpInstrumentation extends Instrumenter.Configurable {
  public AkkaHttpInstrumentation() {
    super("akkahttp");
  }

  // TODO: Disable Instrumentation by default
  // TODO: Use test DSL
  // TODO: Merge into play testing
  // TODO: also check 10.0.8 (play 2.6.0 dep) and latest 10.1

  private static final HelperInjector akkaHttpHelperInjector =
      new HelperInjector(
          AkkaHttpInstrumentation.class.getName() + "$DatadogGraph",
          AkkaHttpInstrumentation.class.getName() + "$AkkaHttpHeaders",
          AkkaHttpInstrumentation.class.getName() + "$DatadogLogic",
          AkkaHttpInstrumentation.class.getName() + "$DatadogLogic$1",
          AkkaHttpInstrumentation.class.getName() + "$DatadogLogic$2",
          AkkaHttpInstrumentation.class.getName() + "$DatadogLogic$3",
          AkkaHttpInstrumentation.class.getName() + "$DatadogLogic$4");

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("akka.http.scaladsl.HttpExt"))
        .transform(DDTransformers.defaultTransformers())
        .transform(akkaHttpHelperInjector)
        .transform(
            DDAdvice.create()
                .advice(
                    named("bindAndHandle")
                        .and(takesArgument(0, named("akka.stream.scaladsl.Flow"))),
                    AkkaHttpAdvice.class.getName()))
        .asDecorator()
        .type(named("akka.stream.impl.fusing.GraphInterpreter"))
        .transform(DDTransformers.defaultTransformers())
        .transform(akkaHttpHelperInjector)
        .transform(
            DDAdvice.create().advice(named("execute"), GraphInterpreterAdvice.class.getName()))
        .asDecorator();
  }

  /** Wrap user's Flow in a datadog graph */
  public static class AkkaHttpAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startSpan(
        @Advice.Argument(value = 0, readOnly = false)
            Flow<HttpRequest, HttpResponse, Object> handler) {
      // recommended way to wrap a flow is to use join with a custom graph stage
      // https://groups.google.com/forum/#!topic/akka-user/phtZM_kuy7o
      handler = handler.join(new DatadogGraph());
    }
  }

  /** Close spans created by DatadogLogic */
  public static class GraphInterpreterAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeScope() {
      if (DatadogLogic.NUM_SCOPES_TO_CLOSE.get() != null) {
        int numScopesToClose = DatadogLogic.NUM_SCOPES_TO_CLOSE.get();
        DatadogLogic.NUM_SCOPES_TO_CLOSE.set(0);
        while (numScopesToClose > 0 && GlobalTracer.get().scopeManager().active() != null) {
          GlobalTracer.get().scopeManager().active().close();
          numScopesToClose--;
        }
      }
    }
  }

  public static class DatadogGraph
      extends GraphStage<BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest>> {
    private final Inlet<HttpResponse> in1 = Inlet.create("datadog.in1");
    private final Outlet<HttpResponse> out1 = Outlet.create("datadog.toWrapped");
    private final Inlet<HttpRequest> in2 = Inlet.create("datadog.fromWrapped");
    private final Outlet<HttpRequest> out2 = Outlet.create("datadog.out2");
    private final BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape =
        BidiShape.of(in1, out1, in2, out2);

    @Override
    public BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape() {
      return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
      return new DatadogLogic(shape());
    }
  }

  /** Stateful logic of the akka http pipeline */
  public static class DatadogLogic extends GraphStageLogic {
    /** Signal the graph logic advice to close the scope at the end of an execution phase. */
    // ideally there would be a way to push a close-scope event after
    // the user's input handler has run, but there doesn't seem to be a way to do that
    public static final ThreadLocal<Integer> NUM_SCOPES_TO_CLOSE = new ThreadLocal<>();

    // safe to use volatile without locking because
    // ordering and number of invocations of handler is guaranteed by akka streams
    // ideally this would be a final variable, but the span cannot be set until
    // the in2 handler is invoked by the graph logic
    private volatile Span span;

    // Response | ->  in1   --> out1  | tcp ->
    // Request  | <-  out2  <-- in2   | <- tcp
    public DatadogLogic(
        final BidiShape<HttpResponse, HttpResponse, HttpRequest, HttpRequest> shape) {
      super(shape);

      setHandler(
          shape.in2(),
          new AbstractInHandler() {
            @Override
            public void onPush() {
              final HttpRequest request = grab(shape.in2());
              createSpan(request);
              push(shape.out2(), request);
            }

            @Override
            public void onUpstreamFailure(Throwable ex) throws Exception {
              finishSpan(ex);
              super.onUpstreamFailure(ex);
            }
          });

      setHandler(
          shape.out2(),
          new AbstractOutHandler() {
            @Override
            public void onPull() {
              pull(shape.in2());
            }

            @Override
            public void onDownstreamFinish() throws Exception {
              // Invoked on errors. Don't complete this stage to allow error-capturing
            }
          });

      setHandler(
          shape.in1(),
          new AbstractInHandler() {
            @Override
            public void onPush() {
              final HttpResponse response = grab(shape.in1());
              finishSpan(response);
              push(shape.out1(), response);
            }

            @Override
            public void onUpstreamFailure(Throwable ex) throws Exception {
              if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
                ((TraceScope) GlobalTracer.get().scopeManager().active())
                    .setAsyncPropagation(false);
              }
              finishSpan(ex);

              super.onUpstreamFailure(ex);
            }
          });

      setHandler(
          shape.out1(),
          new AbstractOutHandler() {
            @Override
            public void onPull() {
              pull(shape.in1());
            }
          });
    }

    private void createSpan(final HttpRequest request) {
      SpanContext extractedContext =
          GlobalTracer.get().extract(Format.Builtin.HTTP_HEADERS, new AkkaHttpHeaders(request));

      span =
          GlobalTracer.get()
              .buildSpan("akkahttp.request")
              .asChildOf(extractedContext)
              .startActive(false)
              .span();
      // close the created scope at the end of the graph execution
      if (null == NUM_SCOPES_TO_CLOSE.get()) {
        NUM_SCOPES_TO_CLOSE.set(1);
      } else {
        NUM_SCOPES_TO_CLOSE.set(NUM_SCOPES_TO_CLOSE.get() + 1);
      }

      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true);
      }

      span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
      span.setTag(Tags.HTTP_METHOD.getKey(), request.method().value());
      span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET);
      span.setTag(Tags.COMPONENT.getKey(), "akkahttp-action");
      span.setTag(Tags.HTTP_URL.getKey(), request.getUri().toString());
    }

    private void finishSpan(final HttpResponse response) {
      if (null != span) {
        stopScopePropagation();
        Tags.HTTP_STATUS.set(span, response.status().intValue());
        span.finish();
        span = null;
      }
    }

    private void finishSpan(final Throwable t) {
      if (null != span) {
        stopScopePropagation();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap("error.object", t));
        Tags.HTTP_STATUS.set(span, 500);
        span.finish();
        span = null;
      }
    }

    private void stopScopePropagation() {
      if (GlobalTracer.get().scopeManager().active() instanceof TraceScope) {
        ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(false);
      }
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
