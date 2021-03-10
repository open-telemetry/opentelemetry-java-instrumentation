/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import java.util.Map;

/** A builder of {@link ArmeriaTracing}. */
public final class ArmeriaTracingBuilder {
  private final OpenTelemetry openTelemetry;
  private Map<String, String> peerServiceMapping = Collections.emptyMap();

  ArmeriaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Used to specify a mapping from hostnames or IP addresses (key of the passed {@code
   * peerServiceMapping} map) to peer services (value of the map). The peer service is added as an
   * attribute to a span whose host or IP match the mapping.
   *
   * @see <a
   *     href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">peer.service
   *     attribute specification</a>
   */
  public ArmeriaTracingBuilder setPeerServiceMapping(Map<String, String> peerServiceMapping) {
    this.peerServiceMapping = peerServiceMapping;
    return this;
  }

  /**
   * Returns a new {@link ArmeriaTracing} with the settings of this {@link ArmeriaTracingBuilder}.
   */
  public ArmeriaTracing build() {
    return new ArmeriaTracing(openTelemetry, peerServiceMapping);
  }
}
