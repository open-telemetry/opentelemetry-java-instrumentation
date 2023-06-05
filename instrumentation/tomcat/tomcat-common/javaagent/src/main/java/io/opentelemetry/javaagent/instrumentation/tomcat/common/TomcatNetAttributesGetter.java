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

public class TomcatNetAttributesGetter implements NetServerAttributesGetter<Request> {

  @Nullable
  @Override
  public String getProtocolName(Request request) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request request) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getHostName(Request request) {
    return messageBytesToString(request.serverName());
  }

  @Override
  public Integer getHostPort(Request request) {
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getSockPeerAddr(Request request) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.remoteAddr());
  }

  @Override
  @Nullable
  public Integer getSockPeerPort(Request request) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getSockHostAddr(Request request) {
    request.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.localAddr());
  }

  @Nullable
  @Override
  public Integer getSockHostPort(Request request) {
    request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
    return request.getLocalPort();
  }
}
