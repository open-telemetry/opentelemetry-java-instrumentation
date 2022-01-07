/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.emptyMap;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 *
 * <p>Peer service name mappings can be configured using the {@code
 * otel.instrumentation.common.peer-service-mapping} configuration property. The format used is a
 * comma-separated list of {@code host=name} pairs.
 */
public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {
  private static final Map<String, String> JAVAAGENT_PEER_SERVICE_MAPPING =
      Config.get().getMap("otel.instrumentation.common.peer-service-mapping", emptyMap());

  private final Map<String, String> peerServiceMapping;
  private final NetClientAttributesAdapter<REQUEST, RESPONSE> attributesAdapter;

  // visible for tests
  PeerServiceAttributesExtractor(
      Map<String, String> peerServiceMapping,
      NetClientAttributesAdapter<REQUEST, RESPONSE> netAttributesAdapter) {
    this.peerServiceMapping = peerServiceMapping;
    this.attributesAdapter = netAttributesAdapter;
  }

  /**
   * Returns a new {@link PeerServiceAttributesExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> PeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
      NetClientAttributesAdapter<REQUEST, RESPONSE> netAttributesAdapter) {
    return new PeerServiceAttributesExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netAttributesAdapter);
  }

  @Override
  public void onStart(AttributesBuilder attributes, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    if (peerServiceMapping.isEmpty()) {
      // optimization for common case
      return;
    }

    String peerName = attributesAdapter.peerName(request, response);
    String peerService = mapToPeerService(peerName);
    if (peerService == null) {
      String peerIp = attributesAdapter.peerIp(request, response);
      peerService = mapToPeerService(peerIp);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  @Nullable
  private String mapToPeerService(String endpoint) {
    if (endpoint == null) {
      return null;
    }
    return peerServiceMapping.get(endpoint);
  }
}
