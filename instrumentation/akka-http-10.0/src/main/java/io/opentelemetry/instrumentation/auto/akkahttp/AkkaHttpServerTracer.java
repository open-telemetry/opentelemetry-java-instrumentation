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

package io.opentelemetry.instrumentation.auto.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class AkkaHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, HttpRequest, Void> {
  public static final AkkaHttpServerTracer TRACER = new AkkaHttpServerTracer();

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeader(name).map(HttpHeader::value).orElse(null);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  protected void attachServerContext(Context context, Void none) {}

  @Override
  public Context getServerContext(Void none) {
    return null;
  }

  @Override
  protected URI url(HttpRequest httpRequest) throws URISyntaxException {
    return new URI(httpRequest.uri().toString());
  }

  @Override
  protected String peerHostIP(HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected String flavor(HttpRequest connection, HttpRequest request) {
    return connection.protocol().value();
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return AkkaHttpServerHeaders.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.akka-http-10.0";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected Integer peerPort(HttpRequest httpRequest) {
    return null;
  }
}
