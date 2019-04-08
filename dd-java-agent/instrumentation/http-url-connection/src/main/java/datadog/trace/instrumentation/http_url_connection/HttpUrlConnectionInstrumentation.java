package datadog.trace.instrumentation.http_url_connection;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class HttpUrlConnectionInstrumentation extends Instrumenter.Default {

  public HttpUrlConnectionInstrumentation() {
    super("httpurlconnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("java.net.HttpURLConnection"))
        // This class is a simple delegator. Skip because it does not update its `connected` field.
        .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".HttpUrlConnectionDecorator",
      HttpUrlConnectionInstrumentation.class.getName() + "$HeadersInjectAdapter",
      HttpUrlConnectionInstrumentation.class.getName() + "$HttpUrlState",
      HttpUrlConnectionInstrumentation.class.getName() + "$HttpUrlState$1",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.net.HttpURLConnection", getClass().getName() + "$HttpUrlState");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("connect").or(named("getOutputStream")).or(named("getInputStream"))),
        HttpUrlConnectionAdvice.class.getName());
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HttpUrlState methodEnter(
        @Advice.This final HttpURLConnection thiz,
        @Advice.FieldValue("connected") final boolean connected) {

      final ContextStore<HttpURLConnection, HttpUrlState> contextStore =
          InstrumentationContext.get(HttpURLConnection.class, HttpUrlState.class);
      final HttpUrlState state = contextStore.putIfAbsent(thiz, HttpUrlState.FACTORY);

      synchronized (state) {
        /*
         * AgentWriter uses HttpURLConnection to report to the trace-agent. We don't want to trace
         * those requests. Check after the connected test above because getRequestProperty will
         * throw an exception if already connected.
         */
        final boolean isTraceRequest =
            Thread.currentThread().getName().equals("dd-agent-writer")
                || (!connected && thiz.getRequestProperty("Datadog-Meta-Lang") != null);
        if (isTraceRequest) {
          state.finish();
          return null;
        }

        final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpURLConnection.class);
        if (callDepth > 0) {
          return null;
        }

        if (!state.hasSpan() && !state.isFinished()) {
          final Span span = state.startSpan(thiz);
          if (!connected) {
            GlobalTracer.get()
                .inject(
                    span.context(), Format.Builtin.HTTP_HEADERS, new HeadersInjectAdapter(thiz));
          }
        }
        return state;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final HttpUrlState state,
        @Advice.FieldValue("responseCode") final int responseCode,
        @Advice.Thrown final Throwable throwable,
        @Advice.Origin("#m") final String methodName) {

      if (state == null) {
        return;
      }

      synchronized (state) {
        if (state.hasSpan() && !state.isFinished()) {
          if (throwable != null) {
            state.finishSpan(throwable);
          } else if ("getInputStream".equals(methodName)) {
            state.finishSpan(responseCode);
          }
        }
      }

      CallDepthThreadLocalMap.reset(HttpURLConnection.class);
    }
  }

  public static class HeadersInjectAdapter implements TextMap {

    private final HttpURLConnection connection;

    public HeadersInjectAdapter(final HttpURLConnection connection) {
      this.connection = connection;
    }

    @Override
    public void put(final String key, final String value) {
      try {
        connection.setRequestProperty(key, value);
      } catch (final IllegalStateException e) {
        // There are cases when this can through an exception. E.g. some implementations have
        // 'connecting' state. Just guard against that here, there is not much we can do at this
        // point.
      }
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }
  }

  public static class HttpUrlState {

    public static final String OPERATION_NAME = "http.request";

    public static final ContextStore.Factory<HttpUrlState> FACTORY =
        new ContextStore.Factory<HttpUrlState>() {
          @Override
          public HttpUrlState create() {
            return new HttpUrlState();
          }
        };

    private volatile Span span = null;
    private volatile boolean finished = false;

    public Span startSpan(final HttpURLConnection connection) {
      final Tracer.SpanBuilder builder = GlobalTracer.get().buildSpan(OPERATION_NAME);
      span = builder.start();
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, connection);
        return span;
      }
    }

    public boolean hasSpan() {
      return span != null;
    }

    public boolean isFinished() {
      return finished;
    }

    public void finish() {
      finished = true;
    }

    public void finishSpan(final Throwable throwable) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
        span = null;
        finished = true;
      }
    }

    public void finishSpan(final int responseCode) {
      /*
       * responseCode field is sometimes not populated.
       * We can't call getResponseCode() due to some unwanted side-effects
       * (e.g. breaks getOutputStream).
       */
      if (responseCode > 0) {
        try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
          DECORATE.onResponse(span, responseCode);
          DECORATE.beforeFinish(span);
          span.finish();
          span = null;
          finished = true;
        }
      }
    }
  }
}
