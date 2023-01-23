/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class LibertyDispatcherNetAttributesGetter
    implements NetServerAttributesGetter<LibertyRequest> {

  @Override
  public String getTransport(LibertyRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(LibertyRequest request) {
    return request.request().getURLHost();
  }

  @Override
  public Integer getHostPort(LibertyRequest request) {
    return request.request().getURLPort();
  }

  @Override
  @Nullable
  public String getSockPeerAddr(LibertyRequest request) {
    return request.dispatcher().getRemoteHostAddress();
  }

  @Override
  public Integer getSockPeerPort(LibertyRequest request) {
    return request.dispatcher().getRemotePort();
  }

  @Nullable
  @Override
  public String getSockHostAddr(LibertyRequest request) {
    return request.dispatcher().getLocalHostAddress();
  }

  @Nullable
  @Override
  public Integer getSockHostPort(LibertyRequest request) {
    return request.dispatcher().getLocalPort();
  }
}
