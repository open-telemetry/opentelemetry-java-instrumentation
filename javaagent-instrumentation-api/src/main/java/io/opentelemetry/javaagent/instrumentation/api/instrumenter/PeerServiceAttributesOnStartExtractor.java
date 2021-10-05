/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 *
 * <p>Peer service name mappings can be configured using the {@code
 * otel.instrumentation.common.peer-service-mapping} configuration property. The format used is a
 * comma-separated list of {@code host=name} pairs.
 */
public final class PeerServiceAttributesOnStartExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {
  private static final Map<String, String> JAVAAGENT_PEER_SERVICE_MAPPING =
      Config.get().getMap("otel.instrumentation.common.peer-service-mapping");

  private final Map<String, String> peerServiceMapping;
  private final NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor;

  // visible for tests
  PeerServiceAttributesOnStartExtractor(
      Map<String, String> peerServiceMapping,
      NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    this.peerServiceMapping = peerServiceMapping;
    this.netAttributesExtractor = netAttributesExtractor;
  }

  /**
   * Returns a new {@link PeerServiceAttributesOnStartExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> PeerServiceAttributesOnStartExtractor<REQUEST, RESPONSE> create(
      NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    return new PeerServiceAttributesOnStartExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netAttributesExtractor);
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
