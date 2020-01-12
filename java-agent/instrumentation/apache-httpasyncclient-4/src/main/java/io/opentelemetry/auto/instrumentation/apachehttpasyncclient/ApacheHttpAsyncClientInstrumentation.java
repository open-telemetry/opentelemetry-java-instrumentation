package io.opentelemetry.auto.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.auto.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.apachehttpasyncclient.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.io.IOException;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

@AutoService(Instrumenter.class)
public class ApacheHttpAsyncClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpAsyncClientInstrumentation() {
    super("httpasyncclient", "apache-httpasyncclient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.http.nio.client.HttpAsyncClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter",
      getClass().getName() + "$DelegatingRequestProducer",
      getClass().getName() + "$TraceContinuedFutureCallback",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".ApacheHttpAsyncClientDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback"))),
        ApacheHttpAsyncClientInstrumentation.class.getName() + "$ClientAdvice");
  }

  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentSpan methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(2) final HttpContext context,
        @Advice.Argument(value = 3, readOnly = false) FutureCallback<?> futureCallback) {

      final AgentSpan parentSpan = activeSpan();
      final AgentSpan clientSpan = startSpan("http.request");
      DECORATE.afterStart(clientSpan);

      requestProducer = new DelegatingRequestProducer(clientSpan, requestProducer);
      futureCallback =
          new TraceContinuedFutureCallback(parentSpan, clientSpan, context, futureCallback);

      return clientSpan;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentSpan span,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    final AgentSpan span;
    final HttpAsyncRequestProducer delegate;

    public DelegatingRequestProducer(
        final AgentSpan span, final HttpAsyncRequestProducer delegate) {
      this.span = span;
      this.delegate = delegate;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      final HttpRequest request = delegate.generateRequest();
      DECORATE.onRequest(span, request);

      propagate().inject(span, request, SETTER);

      return request;
    }

    @Override
    public void produceContent(final ContentEncoder encoder, final IOControl ioctrl)
        throws IOException {
      delegate.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted(final HttpContext context) {
      delegate.requestCompleted(context);
    }

    @Override
    public void failed(final Exception ex) {
      delegate.failed(ex);
    }

    @Override
    public boolean isRepeatable() {
      return delegate.isRepeatable();
    }

    @Override
    public void resetRequest() throws IOException {
      delegate.resetRequest();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  public static class TraceContinuedFutureCallback<T> implements FutureCallback<T> {
    private final AgentSpan parentSpan;
    private final AgentSpan clientSpan;
    private final HttpContext context;
    private final FutureCallback<T> delegate;

    public TraceContinuedFutureCallback(
        final AgentSpan parentSpan,
        final AgentSpan clientSpan,
        final HttpContext context,
        final FutureCallback<T> delegate) {
      this.parentSpan = parentSpan;
      this.clientSpan = clientSpan;
      this.context = context;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    @Override
    public void completed(final T result) {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.finish(); // Finish span before calling delegate

      if (parentSpan == null) {
        completeDelegate(result);
      } else {
        try (final AgentScope scope = activateSpan(parentSpan, false)) {
          completeDelegate(result);
        }
      }
    }

    @Override
    public void failed(final Exception ex) {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.onError(clientSpan, ex);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.finish(); // Finish span before calling delegate

      if (parentSpan == null) {
        failDelegate(ex);
      } else {
        try (final AgentScope scope = activateSpan(parentSpan, false)) {
          failDelegate(ex);
        }
      }
    }

    @Override
    public void cancelled() {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.finish(); // Finish span before calling delegate

      if (parentSpan == null) {
        cancelDelegate();
      } else {
        try (final AgentScope scope = activateSpan(parentSpan, false)) {
          cancelDelegate();
        }
      }
    }

    private void completeDelegate(final T result) {
      if (delegate != null) {
        delegate.completed(result);
      }
    }

    private void failDelegate(final Exception ex) {
      if (delegate != null) {
        delegate.failed(ex);
      }
    }

    private void cancelDelegate() {
      if (delegate != null) {
        delegate.cancelled();
      }
    }
  }
}
