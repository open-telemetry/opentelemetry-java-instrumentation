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

package io.opentelemetry.instrumentation.auto.kubernetesclient;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Span;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class KubernetesClientTracer extends HttpClientTracer<Request, Request, Response> {
  public static final KubernetesClientTracer TRACER = new KubernetesClientTracer();

  @Override
  protected String method(Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected Integer status(Response httpResponse) {
    return httpResponse.code();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.header(name);
  }

  @Override
  protected Setter<Request> getSetter() {
    // TODO (trask) no propagation implemented yet?
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.kubernetes-client-7.0";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onRequest(Span span, Request request) {
    return super.onRequest(span, request);
  }

  /**
   * This method is used to generate an acceptable CLIENT span (operation) name based on a given
   * KubernetesRequestDigest.
   */
  public Span startSpan(KubernetesRequestDigest digest) {
    return tracer
        .spanBuilder(digest.toString())
        .setSpanKind(CLIENT)
        .setAttribute("namespace", digest.getResourceMeta().getNamespace())
        .setAttribute("name", digest.getResourceMeta().getName())
        .startSpan();
  }
}
