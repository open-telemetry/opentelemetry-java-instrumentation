/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LibertyDispatcherNetAttributesExtractor
    extends NetAttributesExtractor<LibertyRequest, LibertyResponse> {

  public LibertyDispatcherNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Override
  public String transport(LibertyRequest libertyRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return libertyRequest.peerName();
  }

  @Override
  public @Nullable Integer peerPort(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return libertyRequest.getServerPort();
  }

  @Override
  public @Nullable String peerIp(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return libertyRequest.peerIp();
  }
}
