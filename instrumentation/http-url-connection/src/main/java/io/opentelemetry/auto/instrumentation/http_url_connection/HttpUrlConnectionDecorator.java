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
package io.opentelemetry.auto.instrumentation.http_url_connection;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.HttpsURLConnection;

public class HttpUrlConnectionDecorator extends HttpClientDecorator<HttpURLConnection, Integer> {
  public static final HttpUrlConnectionDecorator DECORATE = new HttpUrlConnectionDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.http-url-connection");

  @Override
  protected String getComponentName() {
    return "http-url-connection";
  }

  @Override
  protected String method(final HttpURLConnection connection) {
    return connection.getRequestMethod();
  }

  @Override
  protected URI url(final HttpURLConnection connection) throws URISyntaxException {
    return connection.getURL().toURI();
  }

  @Override
  protected String hostname(final HttpURLConnection connection) {
    return connection.getURL().getHost();
  }

  @Override
  protected Integer port(final HttpURLConnection connection) {
    final int port = connection.getURL().getPort();
    if (port > 0) {
      return port;
    } else if (connection instanceof HttpsURLConnection) {
      return 443;
    } else {
      return 80;
    }
  }

  @Override
  protected Integer status(final Integer status) {
    return status;
  }
}
