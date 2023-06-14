/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import com.noelios.restlet.http.HttpCall;
import com.noelios.restlet.http.HttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class RestletNetAttributesGetter implements NetServerAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    String protocol = getProtocolString(request);
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    String protocol = getProtocolString(request);
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  private static String getProtocolString(Request request) {
    return (String) request.getAttributes().get("org.restlet.http.version");
  }

  @Nullable
  @Override
  public String getServerAddress(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getHostDomain();
  }

  @Nullable
  @Override
  public Integer getServerPort(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getServerPort();
  }

  @Override
  @Nullable
  public String getClientSocketAddress(Request request, @Nullable Response response) {
    return request.getClientInfo().getAddress();
  }

  @Override
  public Integer getClientSocketPort(Request request, @Nullable Response response) {
    return request.getClientInfo().getPort();
  }

  @Nullable
  @Override
  public String getServerSocketAddress(Request request, @Nullable Response response) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getServerAddress();
  }

  @Nullable
  private static HttpCall httpCall(Request request) {
    if (request instanceof HttpRequest) {
      return ((HttpRequest) request).getHttpCall();
    }
    return null;
  }
}
