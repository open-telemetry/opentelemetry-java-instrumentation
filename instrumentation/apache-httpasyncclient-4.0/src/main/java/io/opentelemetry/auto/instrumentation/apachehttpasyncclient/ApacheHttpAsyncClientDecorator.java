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
package io.opentelemetry.auto.instrumentation.apachehttpasyncclient;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class ApacheHttpAsyncClientDecorator extends HttpClientDecorator<HttpRequest, HttpContext> {
  public static final ApacheHttpAsyncClientDecorator DECORATE =
      new ApacheHttpAsyncClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.apache-httpasyncclient-4.0");

  @Override
  protected String getComponentName() {
    return "apache-httpasyncclient";
  }

  @Override
  protected String method(final HttpRequest request) {
    if (request instanceof HttpUriRequest) {
      return ((HttpUriRequest) request).getMethod();
    } else {
      final RequestLine requestLine = request.getRequestLine();
      return requestLine == null ? null : requestLine.getMethod();
    }
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    /*
     * Note: this is essentially an optimization: HttpUriRequest allows quicker access to required information.
     * The downside is that we need to load HttpUriRequest which essentially means we depend on httpasyncclient
     * library depending on httpclient library. Currently this seems to be the case.
     */
    if (request instanceof HttpUriRequest) {
      return ((HttpUriRequest) request).getURI();
    } else {
      final RequestLine requestLine = request.getRequestLine();
      return requestLine == null ? null : new URI(requestLine.getUri());
    }
  }

  @Override
  protected String hostname(final HttpRequest request) {
    try {
      final URI uri = url(request);
      if (uri != null) {
        return uri.getHost();
      } else {
        return null;
      }
    } catch (final URISyntaxException e) {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpRequest request) {
    try {
      final URI uri = url(request);
      if (uri != null) {
        return uri.getPort();
      } else {
        return null;
      }
    } catch (final URISyntaxException e) {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpContext context) {
    final Object responseObject = context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    if (responseObject instanceof HttpResponse) {
      final StatusLine statusLine = ((HttpResponse) responseObject).getStatusLine();
      if (statusLine != null) {
        return statusLine.getStatusCode();
      }
    }
    return null;
  }
}
