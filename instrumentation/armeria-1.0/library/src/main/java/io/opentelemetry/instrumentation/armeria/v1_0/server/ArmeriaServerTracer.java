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

package io.opentelemetry.instrumentation.armeria.v1_0.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.api.decorator.HttpServerTracer;
import io.opentelemetry.trace.Tracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

public class ArmeriaServerTracer
    extends HttpServerTracer<HttpRequest, RequestLog, ServiceRequestContext, Void> {

  ArmeriaServerTracer() {}

  ArmeriaServerTracer(Tracer tracer) {
    super(tracer);
  }

  @Override
  public Context getServerContext(Void ctx) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria-1.0";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected Integer peerPort(ServiceRequestContext ctx) {
    SocketAddress socketAddress = ctx.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetAddress = (InetSocketAddress) socketAddress;
      return inetAddress.getPort();
    }
    return null;
  }

  @Override
  protected String peerHostIP(ServiceRequestContext ctx) {
    SocketAddress socketAddress = ctx.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetAddress = (InetSocketAddress) socketAddress;
      return inetAddress.getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected String flavor(ServiceRequestContext ctx, HttpRequest req) {
    return ctx.sessionProtocol().toString();
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return ArmeriaGetter.INSTANCE;
  }

  @Override
  protected URI url(HttpRequest req) {
    return req.uri();
  }

  @Override
  protected String method(HttpRequest req) {
    return req.method().name();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(RequestLog httpResponse) {
    return httpResponse.responseHeaders().status().code();
  }

  @Override
  protected void attachServerContext(Context context, Void ctx) {}

  private static class ArmeriaGetter implements Getter<HttpRequest> {

    private static final ArmeriaGetter INSTANCE = new ArmeriaGetter();

    @Override
    public String get(HttpRequest carrier, String key) {
      return carrier.headers().get(key);
    }
  }
}
