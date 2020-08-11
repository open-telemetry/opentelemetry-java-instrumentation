/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.decorator.HttpClientTracer;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;
import software.amazon.awssdk.http.SdkHttpHeaders;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkClientTracer extends HttpClientTracer<SdkHttpRequest, SdkHttpResponse> {
  static final AwsSdkClientTracer TRACER = new AwsSdkClientTracer();

  // Certain headers in the request like User-Agent are only available after execution.
  Span afterExecution(final Span span, final SdkHttpRequest request) {
    SemanticAttributes.HTTP_USER_AGENT.set(span, requestHeader(request, USER_AGENT));
    return span;
  }

  @Override
  protected String method(final SdkHttpRequest request) {
    return request.method().name();
  }

  @Override
  protected URI url(final SdkHttpRequest request) {
    return request.getUri();
  }

  @Override
  protected Integer status(final SdkHttpResponse response) {
    return response.statusCode();
  }

  @Override
  protected String requestHeader(SdkHttpRequest sdkHttpRequest, String name) {
    return header(sdkHttpRequest, name);
  }

  @Override
  protected String responseHeader(SdkHttpResponse sdkHttpResponse, String name) {
    return header(sdkHttpResponse, name);
  }

  @Override
  protected Setter<SdkHttpRequest> getSetter() {
    return null;
  }

  private static String header(SdkHttpHeaders headers, String name) {
    return headers.firstMatchingHeader(name).orElse(null);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.aws-sdk-2.2";
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * io.grpc.Context }, or an invalid {@link Span} otherwise.
   */
  public Span getOrCreateSpan(String name, Tracer tracer) {
    io.grpc.Context context = io.grpc.Context.current();
    Span clientSpan = CONTEXT_CLIENT_SPAN_KEY.get(context);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    Span current = TracingContextUtils.getSpan(context);
    return tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(current).startSpan();
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onRequest(Span span, SdkHttpRequest sdkHttpRequest) {
    return super.onRequest(span, sdkHttpRequest);
  }
}
