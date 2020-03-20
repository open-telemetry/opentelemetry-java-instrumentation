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
package io.opentelemetry.auto.instrumentation.playws;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWSClientDecorator extends HttpClientDecorator<Request, Response> {
  public static final PlayWSClientDecorator DECORATE = new PlayWSClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.play-ws-2.1");

  @Override
  protected String method(final Request request) {
    return request.getMethod();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return request.getUri().toJavaNetURI();
  }

  @Override
  protected String hostname(final Request request) {
    return request.getUri().getHost();
  }

  @Override
  protected Integer port(final Request request) {
    return request.getUri().getPort();
  }

  @Override
  protected Integer status(final Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String getComponentName() {
    return "play-ws";
  }
}
