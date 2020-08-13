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

package io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.decorator.HttpClientTracer;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap.Depth;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URIException;

public class CommonsHttpClientTracer extends HttpClientTracer<HttpMethod, HttpMethod> {
  public static final CommonsHttpClientTracer TRACER = new CommonsHttpClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.apache-httpclient-2.0";
  }

  public Depth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(HttpClient.class);
  }

  @Override
  protected String method(final HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected URI url(final HttpMethod httpMethod) throws URISyntaxException {
    try {
      //  org.apache.commons.httpclient.URI -> java.net.URI
      return new URI(httpMethod.getURI().toString());
    } catch (final URIException e) {
      throw new URISyntaxException("", e.getMessage());
    }
  }

  @Override
  protected Integer status(final HttpMethod httpMethod) {
    StatusLine statusLine = httpMethod.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }

  @Override
  protected String requestHeader(HttpMethod httpMethod, String name) {
    Header header = httpMethod.getRequestHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String responseHeader(HttpMethod httpMethod, String name) {
    Header header = httpMethod.getResponseHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected Setter<HttpMethod> getSetter() {
    return HttpHeadersInjectAdapter.SETTER;
  }
}
