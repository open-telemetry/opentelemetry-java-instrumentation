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
package io.opentelemetry.auto.instrumentation.finatra;

import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;

public class FinatraDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final FinatraDecorator DECORATE = new FinatraDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.finatra-2.9");

  @Override
  protected String getComponentName() {
    return "finatra";
  }

  @Override
  protected String method(final Request request) {
    return request.method().name();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return URI.create(request.uri());
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress().getHostAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.remotePort();
  }

  @Override
  protected Integer status(final Response response) {
    return response.statusCode();
  }
}
