/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnStartExtractor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TomcatNetAttributesExtractor extends NetAttributesOnStartExtractor<Request, Response> {

  @Override
  public @Nullable String transport(Request request) {
    // return SemanticAttributes.NetTransportValues.IP_TCP;
    return null;
  }

  @Override
  public @Nullable String peerName(Request request) {
    /*
    request.action(ActionCode.REQ_HOST_ATTRIBUTE, request);
    return request.remoteHost().toString();
     */
    return null;
  }

  @Override
  public @Nullable Integer peerPort(Request request) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Override
  public @Nullable String peerIp(Request request) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return request.remoteAddr().toString();
  }
}
