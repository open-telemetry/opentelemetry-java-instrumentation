/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;

public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {
  private static final Map<String, String> JAVAAGENT_PEER_SERVICE_MAPPING =
      Config.get().getMapProperty("otel.instrumentation.common.peer-service-mapping");

  private final Map<String, String> peerServiceMapping;
  private final NetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor;

  // visible for tests
  PeerServiceAttributesExtractor(
      Map<String, String> peerServiceMapping,
      NetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    this.peerServiceMapping = peerServiceMapping;
    this.netAttributesExtractor = netAttributesExtractor;
  }

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      NetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    return new PeerServiceAttributesExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netAttributesExtractor);
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    onEnd(attributes, request, null);
  }

  @Override
  protected void onEnd(AttributesBuilder attributes, REQUEST request, RESPONSE response) {
    String peerName = netAttributesExtractor.peerName(request, response);
    String peerService = mapToPeerService(peerName);
    if (peerService == null) {
      String peerIp = netAttributesExtractor.peerIp(request, response);
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
