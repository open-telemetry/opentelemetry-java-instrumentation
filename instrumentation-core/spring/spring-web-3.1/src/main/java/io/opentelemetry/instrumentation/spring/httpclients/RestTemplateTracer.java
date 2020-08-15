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

package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.instrumentation.spring.httpclients.HttpHeadersInjectAdapter.SETTER;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

class RestTemplateTracer extends HttpClientTracer<HttpRequest, ClientHttpResponse> {

  public static final RestTemplateTracer TRACER = new RestTemplateTracer();

  public void inject(Context context, HttpRequest request) {
    getPropagators().getHttpTextFormat().inject(context, request, getSetter());
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    return request.getURI();
  }

  @Override
  protected Integer status(ClientHttpResponse response) {
    try {
      return response.getStatusCode().value();
    } catch (IOException e) {
      return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
  }

  @Override
  protected String requestHeader(HttpRequest request, String name) {
    return request.getHeaders().getFirst(name);
  }

  @Override
  protected String responseHeader(ClientHttpResponse response, String name) {
    return response.getHeaders().getFirst(name);
  }

  @Override
  protected Setter<HttpRequest> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spring-web-3.1";
  }
}
