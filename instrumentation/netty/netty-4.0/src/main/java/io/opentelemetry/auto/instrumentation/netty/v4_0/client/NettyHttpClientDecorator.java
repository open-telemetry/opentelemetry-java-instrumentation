/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.netty.v4_0.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final NettyHttpClientDecorator DECORATE = new NettyHttpClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.netty-4.0");

  @Override
  protected String getComponentName() {
    return "netty-client";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    final URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected String hostname(final HttpRequest request) {
    try {
      final URI uri = new URI(request.getUri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        return request.headers().get(HOST).split(":")[0];
      } else {
        return uri.getHost();
      }
    } catch (final Exception e) {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpRequest request) {
    try {
      final URI uri = new URI(request.getUri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        final String[] hostPort = request.headers().get(HOST).split(":");
        return hostPort.length == 2 ? Integer.parseInt(hostPort[1]) : null;
      } else {
        return uri.getPort();
      }
    } catch (final Exception e) {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }
}
