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

package io.opentelemetry.instrumentation.auto.grizzly.client;

import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.decorator.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class GrizzlyClientTracer
    extends HttpClientTracer<Request, FluentCaseInsensitiveStringsMap, Response> {

  public static final GrizzlyClientTracer TRACER = new GrizzlyClientTracer();

  @Override
  protected String method(final Request request) {
    return request.getMethod();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return request.getUri().toJavaNetURI();
  }

  @Override
  protected Integer status(final Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.getHeaders().getFirstValue(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().getFirstValue(name);
  }

  @Override
  protected Setter<FluentCaseInsensitiveStringsMap> getSetter() {
    return GrizzlyInjectAdapter.SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grizzly-client-1.9";
  }
}
