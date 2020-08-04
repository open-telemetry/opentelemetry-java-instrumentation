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

package io.opentelemetry.auto.instrumentation.netty.v3_8.client;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.library.api.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class NettyHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final NettyHttpClientDecorator DECORATE = new NettyHttpClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.netty-3.8");

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().getName();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatus().getCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().get(name);
  }
}
