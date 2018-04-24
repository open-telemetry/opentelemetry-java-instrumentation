package datadog.trace.instrumentation.ratpack.impl;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.asm.Advice;
import ratpack.exec.Promise;
import ratpack.exec.Result;
import ratpack.func.Action;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.http.client.StreamedResponse;

public class RatpackHttpClientAdvice {
  public static class RequestAction implements Action<RequestSpec> {

    private final Action<? super RequestSpec> requestAction;
    private final AtomicReference<Span> spanRef;

    public RequestAction(Action<? super RequestSpec> requestAction, AtomicReference<Span> spanRef) {
      this.requestAction = requestAction;
      this.spanRef = spanRef;
    }

    @Override
    public void execute(RequestSpec requestSpec) throws Exception {
      WrappedRequestSpec wrappedRequestSpec;
      if (requestSpec instanceof WrappedRequestSpec) {
        wrappedRequestSpec = (WrappedRequestSpec) requestSpec;
      } else {
        wrappedRequestSpec =
            new WrappedRequestSpec(
                requestSpec,
                GlobalTracer.get(),
                GlobalTracer.get().scopeManager().active(),
                spanRef);
      }
      requestAction.execute(wrappedRequestSpec);
    }
  }

  public static class ResponseAction implements Action<Result<ReceivedResponse>> {
    private final AtomicReference<Span> spanRef;

    public ResponseAction(AtomicReference<Span> spanRef) {
      this.spanRef = spanRef;
    }

    @Override
    public void execute(Result<ReceivedResponse> result) {
      Span span = spanRef.get();
      if (span == null) {
        return;
      }
      span.finish();
      if (result.isError()) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, result.getThrowable()));
      } else {
        Tags.HTTP_STATUS.set(span, result.getValue().getStatusCode());
      }
    }
  }

  public static class StreamedResponseAction implements Action<Result<StreamedResponse>> {
    private final Span span;

    public StreamedResponseAction(Span span) {
      this.span = span;
    }

    @Override
    public void execute(Result<StreamedResponse> result) {
      span.finish();
      if (result.isError()) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, result.getThrowable()));
      } else {
        Tags.HTTP_STATUS.set(span, result.getValue().getStatusCode());
      }
    }
  }

  public static class RatpackHttpClientRequestAdvice {
    @Advice.OnMethodEnter
    public static AtomicReference<Span> injectTracing(
        @Advice.Argument(value = 1, readOnly = false) Action<? super RequestSpec> requestAction) {
      AtomicReference<Span> span = new AtomicReference<>();

      //noinspection UnusedAssignment
      requestAction = new RequestAction(requestAction, span);

      return span;
    }

    @Advice.OnMethodExit
    public static void finishTracing(
        @Advice.Return(readOnly = false) Promise<ReceivedResponse> promise,
        @Advice.Enter AtomicReference<Span> ref) {

      //noinspection UnusedAssignment
      promise = promise.wiretap(new ResponseAction(ref));
    }
  }

  public static class RatpackHttpClientRequestStreamAdvice {
    @Advice.OnMethodEnter
    public static AtomicReference<Span> injectTracing(
        @Advice.Argument(value = 1, readOnly = false) Action<? super RequestSpec> requestAction) {
      AtomicReference<Span> span = new AtomicReference<>();

      //noinspection UnusedAssignment
      requestAction = new RequestAction(requestAction, span);

      return span;
    }

    @Advice.OnMethodExit
    public static void finishTracing(
        @Advice.Return(readOnly = false) Promise<StreamedResponse> promise,
        @Advice.Enter AtomicReference<Span> ref) {
      Span span = ref.get();
      if (span == null) {
        return;
      }

      //noinspection UnusedAssignment
      promise = promise.wiretap(new StreamedResponseAction(span));
    }
  }

  public static class RatpackHttpGetAdvice {
    @Advice.OnMethodEnter
    public static void ensureGetMethodSet(
        @Advice.Argument(value = 1, readOnly = false) Action<? super RequestSpec> requestAction) {
      //noinspection UnusedAssignment
      requestAction = requestAction.prepend(RequestSpec::get);
    }
  }
}
