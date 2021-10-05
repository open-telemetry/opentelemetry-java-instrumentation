/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.instrumenter;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesClientExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import java.util.Map;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 *
 * <p>Peer service name mappings can be configured using the {@code
 * otel.instrumentation.common.peer-service-mapping} configuration property. The format used is a
 * comma-separated list of {@code host=name} pairs.
 */
public abstract class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {
  private static final Map<String, String> JAVAAGENT_PEER_SERVICE_MAPPING =
      Config.get().getMap("otel.instrumentation.common.peer-service-mapping");

  // package-protected to allow only subclasses in this package
  PeerServiceAttributesExtractor() {}

  /**
   * Returns a new {@link AttributesExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> PeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
      NetAttributesClientExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    return new PeerServiceAttributesClientExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netAttributesExtractor);
  }

  /**
   * Returns a new {@link PeerServiceAttributesExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> PeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
      NetAttributesServerExtractor<REQUEST, RESPONSE> netAttributesExtractor) {
    return new PeerServiceAttributesServerExtractor<>(
        JAVAAGENT_PEER_SERVICE_MAPPING, netAttributesExtractor);
  }
}
