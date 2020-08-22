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

package io.opentelemetry.instrumentation.auto.tomcat;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatHttpServerTracer extends HttpServerTracer<Request, Response, Request, Request> {
  public static final TomcatHttpServerTracer TRACER = new TomcatHttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.tomcat";
  }

  @Override
  public Context getServerContext(Request request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected Integer peerPort(Request request) {
    return wrapRequest(request).getRemotePort();
  }

  @Override
  protected String peerHostIP(Request request) {
    return wrapRequest(request).getRemoteAddr();
  }

  @Override
  protected String flavor(Request request, Request request2) {
    return wrapRequest(request).getProtocol();
  }

  @Override
  protected Getter<Request> getGetter() {
    return RequestGetter.INSTANCE;
  }

  @Override
  protected URI url(Request request)
      throws URISyntaxException {
    org.apache.catalina.connector.Request httpServletRequest = wrapRequest(request);
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  protected String method(Request request) {
    return wrapRequest(request).getMethod();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.getHeader(name);
  }

  @Override
  protected int responseStatus(Response response) {
    return response.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, Request request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  private static org.apache.catalina.connector.Request wrapRequest(Request request) {
    org.apache.catalina.connector.Request servletRequest =
        new org.apache.catalina.connector.Request(null);
    servletRequest.setCoyoteRequest(request);
    return servletRequest;
  }

  private static class RequestGetter implements Getter<Request> {
    public static final RequestGetter INSTANCE = new RequestGetter();

    @Override
    public String get(Request request, String s) {
      return request.getHeader(s);
    }
  }
}
