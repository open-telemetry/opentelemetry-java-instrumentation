/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper.messageBytesToString;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatNetAttributesGetter implements NetServerAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(Request request) {
    return messageBytesToString(request.serverName());
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getClientSocketAddress(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.remoteAddr());
  }

  @Override
  @Nullable
  public Integer getClientSocketPort(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getServerSocketAddress(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.localAddr());
  }

  @Nullable
  @Override
  public Integer getServerSocketPort(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
    return request.getLocalPort();
  }
}
