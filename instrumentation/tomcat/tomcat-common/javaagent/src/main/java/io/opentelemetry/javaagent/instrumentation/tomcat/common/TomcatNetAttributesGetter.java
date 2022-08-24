/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;

public class TomcatNetAttributesGetter implements NetServerAttributesGetter<Request> {

  @Override
  @Nullable
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(Request request) {
    return request.serverName().toString();
  }

  @Override
  public Integer hostPort(Request request) {
    return request.getServerPort();
  }

  @Nullable
  @Override
  public String sockFamily(Request request) {
    return null;
  }

  @Override
  @Nullable
  public String sockPeerAddr(Request request) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return request.remoteAddr().toString();
  }

  @Override
  @Nullable
  public Integer sockPeerPort(Request request) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String sockHostAddr(Request request) {
    request.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, request);
    return request.localAddr().toString();
  }

  @Nullable
  @Override
  public String sockHostName(Request request) {
    request.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, request);
    return request.localName().toString();
  }

  @Nullable
  @Override
  public Integer sockHostPort(Request request) {
    request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
    return request.getLocalPort();
  }
}
