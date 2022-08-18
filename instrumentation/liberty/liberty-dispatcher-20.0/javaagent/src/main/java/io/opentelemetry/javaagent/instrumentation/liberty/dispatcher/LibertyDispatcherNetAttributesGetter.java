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
  public String transport(LibertyRequest libertyRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public Integer sockPeerPort(LibertyRequest libertyRequest) {
    return libertyRequest.peerPort();
  }

  @Override
  @Nullable
  public String sockPeerAddr(LibertyRequest libertyRequest) {
    return libertyRequest.peerIp();
  }
}
