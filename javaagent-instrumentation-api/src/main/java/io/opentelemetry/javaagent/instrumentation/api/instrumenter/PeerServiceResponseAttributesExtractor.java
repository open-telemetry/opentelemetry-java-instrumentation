/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetResponseAttributesExtractor;
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
public final class PeerServiceResponseAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {
  private static final Map<String, String> JAVAAGENT_PEER_SERVICE_MAPPING =
      Config.get().getMap("otel.instrumentation.common.peer-service-mapping");

  private final Map<String, String> peerServiceMapping;
  private final NetResponseAttributesExtractor<REQUEST, RESPONSE> netResponseAttributesExtractor;

  // visible for tests
  PeerServiceResponseAttributesExtractor(
      Map<String, String> peerServiceMapping,
      NetResponseAttributesExtractor<REQUEST, RESPONSE> netResponseAttributesExtractor) {
    this.peerServiceMapping = peerServiceMapping;
    this.netResponseAttributesExtractor = netResponseAttributesExtractor;
  }

  /**
   * Returns a new {@link PeerServiceResponseAttributesExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE>
      PeerServiceResponseAttributesExtractor<REQUEST, RESPONSE> create(
          NetResponseAttributesExtractor<REQUEST, RESPONSE> netResponseAttributesExtractor) {
    return new PeerServiceResponseAttributesExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netResponseAttributesExtractor);
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    onEnd(attributes, request, null, null);
  }

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
