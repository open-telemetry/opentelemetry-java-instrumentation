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
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

public class RestTemplateDecorator extends HttpClientDecorator<HttpRequest, ClientHttpResponse> {

  public static final RestTemplateDecorator DECORATE = new RestTemplateDecorator();

  public void inject(Context context, HttpRequest request) {
    getPropagators().getHttpTextFormat().inject(context, request, SETTER);
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
  protected String userAgent(HttpRequest request) {
    return request.getHeaders().getFirst(USER_AGENT);
  }
}
