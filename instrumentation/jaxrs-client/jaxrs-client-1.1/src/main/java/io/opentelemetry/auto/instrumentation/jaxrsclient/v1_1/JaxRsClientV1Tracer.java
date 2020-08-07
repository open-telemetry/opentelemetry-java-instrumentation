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

package io.opentelemetry.auto.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientTracer;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import java.net.URI;

public class JaxRsClientV1Tracer extends HttpClientTracer<ClientRequest, ClientResponse> {
  public static final JaxRsClientV1Tracer TRACER = new JaxRsClientV1Tracer();

  @Override
  protected String method(final ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final ClientRequest httpRequest) {
    return httpRequest.getURI();
  }

  @Override
  protected Integer status(final ClientResponse clientResponse) {
    return clientResponse.getStatus();
  }

  @Override
  protected String requestHeader(ClientRequest clientRequest, String name) {
    Object header = clientRequest.getHeaders().getFirst(name);
    return header != null ? header.toString() : null;
  }

  @Override
  protected String responseHeader(ClientResponse clientResponse, String name) {
    return clientResponse.getHeaders().getFirst(name);
  }

  @Override
  protected Setter<ClientRequest> getSetter() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jaxrs-client-1.1";
  }
}
