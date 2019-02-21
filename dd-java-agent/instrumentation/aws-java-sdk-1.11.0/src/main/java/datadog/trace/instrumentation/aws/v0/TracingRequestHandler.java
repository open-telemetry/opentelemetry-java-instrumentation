/*
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
