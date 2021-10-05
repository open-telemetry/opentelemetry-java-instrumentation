/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

final class PeerServiceAttributesServerExtractor<REQUEST, RESPONSE>
    extends PeerServiceAttributesExtractor<REQUEST, RESPONSE> {

  private final Map<String, String> peerServiceMapping;
  private final NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor;

  PeerServiceAttributesServerExtractor(
      Map<String, String> peerServiceMapping,
      NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    this.peerServiceMapping = peerServiceMapping;
    this.netAttributesExtractor = netAttributesExtractor;
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    String peerName = netAttributesExtractor.peerName(request);
    String peerService = mapToPeerService(peerName);
    if (peerService == null) {
      String peerIp = netAttributesExtractor.peerIp(request);
      peerService = mapToPeerService(peerIp);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  private String mapToPeerService(String endpoint) {
    if (endpoint == null) {
      return null;
    }
    return peerServiceMapping.get(endpoint);
  }
}
