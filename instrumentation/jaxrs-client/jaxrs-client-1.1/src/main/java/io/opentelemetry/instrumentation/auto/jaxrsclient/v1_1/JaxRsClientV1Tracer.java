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

package io.opentelemetry.instrumentation.auto.jaxrsclient.v1_1;

import static io.opentelemetry.instrumentation.auto.jaxrsclient.v1_1.InjectAdapter.SETTER;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import javax.ws.rs.core.MultivaluedMap;

public class JaxRsClientV1Tracer
    extends HttpClientTracer<ClientRequest, MultivaluedMap<String, Object>, ClientResponse> {
  public static final JaxRsClientV1Tracer TRACER = new JaxRsClientV1Tracer();

  @Override
  protected String method(ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(ClientRequest httpRequest) {
    return httpRequest.getURI();
  }

  @Override
  protected Integer status(ClientResponse clientResponse) {
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
  protected Setter<MultivaluedMap<String, Object>> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jaxrs-client-1.1";
  }
}
