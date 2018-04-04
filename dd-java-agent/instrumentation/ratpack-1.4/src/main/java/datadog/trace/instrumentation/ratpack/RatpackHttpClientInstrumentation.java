package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static datadog.trace.instrumentation.ratpack.RatpackInstrumentation.ACTION_TYPE_DESCRIPTION;
import static datadog.trace.instrumentation.ratpack.RatpackInstrumentation.EXEC_NAME;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import ratpack.exec.Promise;
import ratpack.exec.Result;
import ratpack.func.Action;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.http.client.StreamedResponse;

@AutoService(Instrumenter.class)
public final class RatpackHttpClientInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector HTTP_CLIENT_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.RatpackHttpClientInstrumentation$RatpackHttpClientRequestAdvice",
          "datadog.trace.instrumentation.ratpack.RatpackHttpClientInstrumentation$RatpackHttpClientRequestStreamAdvice",
          "datadog.trace.instrumentation.ratpack.RatpackHttpClientInstrumentation$RatpackHttpGetAdvice",
          "datadog.trace.instrumentation.ratpack.RatpackHttpClientInstrumentation$GetRequestSpecAction",
          "datadog.trace.instrumentation.ratpack.RatapckInstrumentationUtils");
  public static final TypeDescription.ForLoadedType URI_TYPE_DESCRIPTION =
      new TypeDescription.ForLoadedType(URI.class);

  public RatpackHttpClientInstrumentation() {
    super(EXEC_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {

    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("ratpack.http.client.HttpClient"))),
            classLoaderHasClasses(
                "ratpack.exec.Promise",
                "ratpack.exec.Result",
                "ratpack.func.Action",
                "ratpack.http.client.ReceivedResponse",
                "ratpack.http.client.RequestSpec",
                "ratpack.http.client.StreamedResponse",
                "ratpack.http.Request",
                "ratpack.func.Function",
                "ratpack.http.HttpMethod",
                "ratpack.http.MutableHeaders",
                "com.google.common.collect.ListMultimap"))
        .transform(HTTP_CLIENT_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("request")
                        .and(takesArguments(URI_TYPE_DESCRIPTION, ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpClientRequestAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("requestStream")
                        .and(takesArguments(URI_TYPE_DESCRIPTION, ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpClientRequestStreamAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("get").and(takesArguments(URI_TYPE_DESCRIPTION, ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpGetAdvice.class.getName()))
        .asDecorator();
  }

  public static class RequestAction implements Action<RequestSpec> {

    private final Action<? super RequestSpec> requestAction;
    private final AtomicReference<Span> spanRef;

    public RequestAction(Action<? super RequestSpec> requestAction, AtomicReference<Span> spanRef) {
      this.requestAction = requestAction;
      this.spanRef = spanRef;
    }

    @Override
    public void execute(RequestSpec requestSpec) throws Exception {
      requestAction.execute(
          new WrappedRequestSpec(
              requestSpec,
              GlobalTracer.get(),
              GlobalTracer.get().scopeManager().active(),
              spanRef));
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
        span.log(RatapckInstrumentationUtils.errorLogs(result.getThrowable()));
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
        span.log(RatapckInstrumentationUtils.errorLogs(result.getThrowable()));
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
      requestAction = requestAction.prepend(new GetRequestSpecAction());
    }
  }

  /** This was a method reference but we can't use Java 8 due to Java 7 support */
  public static class GetRequestSpecAction implements Action<RequestSpec> {
    @Override
    public void execute(RequestSpec requestSpec) {
      requestSpec.get();
    }
  }
}
