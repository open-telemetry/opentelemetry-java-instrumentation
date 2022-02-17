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

  @Override
  @Nullable
  public String peerName(Request request) {
    // not using request.action(ActionCode.REQ_HOST_ATTRIBUTE, request) since that calls
    // InetAddress.getHostName() which trigger reverse name lookup
    return null;
  }

  @Override
  @Nullable
  public Integer peerPort(Request request) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Override
  @Nullable
  public String peerIp(Request request) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return request.remoteAddr().toString();
  }
}
