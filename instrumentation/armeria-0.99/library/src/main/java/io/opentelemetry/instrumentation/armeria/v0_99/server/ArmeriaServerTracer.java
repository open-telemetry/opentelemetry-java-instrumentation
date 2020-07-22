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

package io.opentelemetry.instrumentation.armeria.v0_99.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.util.Version;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.instrumentation.armeria.v0_99.internal.ContextUtil;
import io.opentelemetry.trace.Tracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

final class ArmeriaServerTracer
    extends HttpServerTracer<HttpRequest, ServiceRequestContext, RequestContext> {

  ArmeriaServerTracer() {}

  ArmeriaServerTracer(Tracer tracer) {
    super(tracer);
  }

  @Override
  public Context getServerContext(RequestContext ctx) {
    return ContextUtil.getContext(ctx);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria-0.99";
  }

  @Override
  protected String getVersion() {
    return Version.get("armeria").artifactVersion();
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
  protected void attachServerContext(Context context, RequestContext ctx) {
    ContextUtil.attachContext(context, ctx);
  }

  private enum ArmeriaGetter implements Getter<HttpRequest> {
    INSTANCE;

    @Override
    public String get(HttpRequest carrier, String key) {
      return carrier.headers().get(key);
    }
  }
}
