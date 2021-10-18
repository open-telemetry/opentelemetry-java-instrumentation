/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class LibertyDispatcherNetAttributesExtractor
    extends NetServerAttributesExtractor<LibertyRequest, LibertyResponse> {

  @Override
  public String transport(LibertyRequest libertyRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(LibertyRequest libertyRequest) {
    return libertyRequest.peerName();
  }

  @Override
  @Nullable
  public Integer peerPort(LibertyRequest libertyRequest) {
    return libertyRequest.getServerPort();
  }

  @Override
  @Nullable
  public String peerIp(LibertyRequest libertyRequest) {
    return libertyRequest.peerIp();
  }
}
