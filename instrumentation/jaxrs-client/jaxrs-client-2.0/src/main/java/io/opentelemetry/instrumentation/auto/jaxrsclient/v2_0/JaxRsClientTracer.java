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

package io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0.InjectAdapter.SETTER;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.MultivaluedMap;

public class JaxRsClientTracer
    extends HttpClientTracer<ClientRequestContext, MultivaluedMap, ClientResponseContext> {
  public static final JaxRsClientTracer TRACER = new JaxRsClientTracer();

  @Override
  protected String method(final ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final ClientRequestContext httpRequest) {
    return httpRequest.getUri();
  }

  @Override
  protected Integer status(final ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected String requestHeader(ClientRequestContext clientRequestContext, String name) {
    return clientRequestContext.getHeaderString(name);
  }

  @Override
  protected String responseHeader(ClientResponseContext clientResponseContext, String name) {
    return clientResponseContext.getHeaderString(name);
  }

  @Override
  protected Setter<MultivaluedMap> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jaxrs-client-2.0";
  }
}
