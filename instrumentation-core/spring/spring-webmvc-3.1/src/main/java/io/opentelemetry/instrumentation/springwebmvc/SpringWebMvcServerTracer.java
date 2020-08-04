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

package io.opentelemetry.instrumentation.springwebmvc;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.library.api.decorator.HttpServerTracer;
import io.opentelemetry.instrumentation.servlet.HttpServletRequestGetter;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class SpringWebMvcServerTracer
    extends HttpServerTracer<
        HttpServletRequest, HttpServletResponse, HttpServletRequest, HttpServletRequest> {

  public SpringWebMvcServerTracer(Tracer tracer) {
    super(tracer);
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIP(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return HttpServletRequestGetter.GETTER;
  }

  @Override
  protected URI url(HttpServletRequest request) throws URISyntaxException {
    return new URI(request.getRequestURI());
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  protected String requestHeader(HttpServletRequest httpServletRequest, String name) {
    return httpServletRequest.getHeader(name);
  }

  @Override
  protected int responseStatus(HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, HttpServletRequest request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  protected String flavor(HttpServletRequest connection, HttpServletRequest request) {
    return connection.getProtocol();
  }

  @Override
  public Context getServerContext(HttpServletRequest request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected String getInstrumentationName() {
    return null;
  }

  @Override
  protected String getVersion() {
    return null;
  }
}
