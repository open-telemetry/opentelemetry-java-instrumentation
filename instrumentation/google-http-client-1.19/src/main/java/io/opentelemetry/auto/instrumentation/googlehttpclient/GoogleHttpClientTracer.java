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

package io.opentelemetry.auto.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.decorator.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class GoogleHttpClientTracer extends HttpClientTracer<HttpRequest, HttpResponse> {
  public static final GoogleHttpClientTracer TRACER = new GoogleHttpClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.google-http-client-1.19";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  protected URI url(final HttpRequest httpRequest) throws URISyntaxException {
    // Google uses %20 (space) instead of "+" for spaces in the fragment
    // Add "+" back for consistency with the other http client instrumentations
    String url = httpRequest.getUrl().build();
    String fixedUrl = url.replaceAll("%20", "+");
    return new URI(fixedUrl);
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return header(httpRequest.getHeaders(), name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return header(httpResponse.getHeaders(), name);
  }

  @Override
  protected Setter<HttpRequest> getSetter() {
    return HeadersInjectAdapter.SETTER;
  }

  private static String header(HttpHeaders headers, String name) {
    return headers.getFirstHeaderStringValue(name);
  }
}
