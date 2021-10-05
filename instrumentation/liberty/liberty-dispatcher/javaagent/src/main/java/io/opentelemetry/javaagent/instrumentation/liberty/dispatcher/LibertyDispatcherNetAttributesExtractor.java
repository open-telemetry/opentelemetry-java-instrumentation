/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LibertyDispatcherNetAttributesExtractor
    extends NetAttributesServerExtractor<LibertyRequest, LibertyResponse> {

  @Override
  public String transport(LibertyRequest libertyRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(LibertyRequest libertyRequest) {
    return libertyRequest.peerName();
  }

  @Override
  public @Nullable Integer peerPort(LibertyRequest libertyRequest) {
    return libertyRequest.getServerPort();
  }

  @Override
  public @Nullable String peerIp(LibertyRequest libertyRequest) {
    return libertyRequest.peerIp();
  }
}
