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

package io.opentelemetry.instrumentation.apachehttpclient.v4_0;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

class ApacheHttpClientDecorator extends HttpClientDecorator<HttpUriRequest, HttpResponse> {
  public static final ApacheHttpClientDecorator DECORATE = new ApacheHttpClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.apache-httpclient-4.0");

  public void inject(Context context, HttpUriRequest request) {
    OpenTelemetry.getPropagators()
        .getHttpTextFormat()
        .inject(context, request, HttpHeadersInjectAdapter.SETTER);
  }

  @Override
  protected String method(final HttpUriRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final HttpUriRequest request) {
    return request.getURI();
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatusLine().getStatusCode();
  }

  @Override
  protected String requestHeader(HttpUriRequest request, String name) {
    return header(request, name);
  }

  @Override
  protected String responseHeader(HttpResponse response, String name) {
    return header(response, name);
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }
}
