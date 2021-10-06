/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesClientExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

final class PeerServiceAttributesClientExtractor<REQUEST, RESPONSE>
    extends PeerServiceAttributesExtractor<REQUEST, RESPONSE> {

  private final Map<String, String> peerServiceMapping;
  private final NetAttributesClientExtractor<REQUEST, RESPONSE> netResponseAttributesExtractor;

  PeerServiceAttributesClientExtractor(
      Map<String, String> peerServiceMapping,
      NetAttributesClientExtractor<REQUEST, RESPONSE> netResponseAttributesExtractor) {
    this.peerServiceMapping = peerServiceMapping;
    this.netResponseAttributesExtractor = netResponseAttributesExtractor;
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    String peerName = netResponseAttributesExtractor.peerName(request, response);
    String peerService = mapToPeerService(peerName);
    if (peerService == null) {
      String peerIp = netResponseAttributesExtractor.peerIp(request, response);
      peerService = mapToPeerService(peerIp);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  private String mapToPeerService(String endpoint) {
    if (endpoint == null) {
      return null;
    }
    return peerServiceMapping.get(endpoint);
  }
}
