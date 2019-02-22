package datadog.trace.instrumentation.aws.v0;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.Scope;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.util.GlobalTracer;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {
  public static TracingRequestHandler INSTANCE = new TracingRequestHandler();

  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  private static final HandlerContextKey<Scope> SCOPE_CONTEXT_KEY =
      new HandlerContextKey<>("DatadogScope");

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  /** {@inheritDoc} */
  @Override
  public void beforeRequest(final Request<?> request) {
    final Scope scope = GlobalTracer.get().buildSpan("aws.command").startActive(true);
    AwsSdkClientDecorator.INSTANCE.afterStart(scope.span());
    AwsSdkClientDecorator.INSTANCE.onRequest(scope.span(), request);

    // We inject headers at aws-client level because aws requests may be signed and adding headers
    // on http-client level may break signature.
    GlobalTracer.get()
        .inject(
            scope.span().context(),
            Format.Builtin.HTTP_HEADERS,
            new TextMapInjectAdapter(request.getHeaders()));

    request.addHandlerContext(SCOPE_CONTEXT_KEY, scope);
  }

  /** {@inheritDoc} */
  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    final Scope scope = request.getHandlerContext(SCOPE_CONTEXT_KEY);
    AwsSdkClientDecorator.INSTANCE.onResponse(scope.span(), response);
    AwsSdkClientDecorator.INSTANCE.beforeFinish(scope.span());
    scope.close();
  }

  /** {@inheritDoc} */
  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    final Scope scope = request.getHandlerContext(SCOPE_CONTEXT_KEY);
    AwsSdkClientDecorator.INSTANCE.onError(scope.span(), e);
    AwsSdkClientDecorator.INSTANCE.beforeFinish(scope.span());
    scope.close();
  }
}
