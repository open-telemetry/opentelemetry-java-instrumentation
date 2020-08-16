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

package io.opentelemetry.instrumentation.auto.grizzly;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public class GrizzlyHttpServerTracer
    extends HttpServerTracer<
        HttpRequestPacket, HttpResponsePacket, HttpRequestPacket, FilterChainContext> {

  public static final GrizzlyHttpServerTracer TRACER = new GrizzlyHttpServerTracer();

  @Override
  protected String method(HttpRequestPacket httpRequest) {
    return httpRequest.getMethod().getMethodString();
  }

  @Override
  protected String requestHeader(HttpRequestPacket httpRequestPacket, String name) {
    return httpRequestPacket.getHeader(name);
  }

  @Override
  protected int responseStatus(HttpResponsePacket httpResponsePacket) {
    return httpResponsePacket.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, FilterChainContext filterChainContext) {
    filterChainContext.getAttributes().setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  public Context getServerContext(FilterChainContext filterChainContext) {
    Object attribute = filterChainContext.getAttributes().getAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Override
  protected URI url(HttpRequestPacket httpRequest) throws URISyntaxException {
    return new URI(
        (httpRequest.isSecure() ? "https://" : "http://")
            + httpRequest.serverName()
            + ":"
            + httpRequest.getServerPort()
            + httpRequest.getRequestURI()
            + (httpRequest.getQueryString() != null ? "?" + httpRequest.getQueryString() : ""));
  }

  @Override
  protected String peerHostIP(HttpRequestPacket httpRequest) {
    return httpRequest.getRemoteAddress();
  }

  @Override
  protected String flavor(HttpRequestPacket connection, HttpRequestPacket request) {
    return connection.getProtocolString();
  }

  @Override
  protected Getter<HttpRequestPacket> getGetter() {
    return ExtractAdapter.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grizzly-2.0";
  }

  @Override
  protected Integer peerPort(HttpRequestPacket httpRequest) {
    return httpRequest.getRemotePort();
  }
}
