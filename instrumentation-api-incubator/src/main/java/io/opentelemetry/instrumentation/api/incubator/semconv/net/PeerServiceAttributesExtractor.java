/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.instrumentation.api.incubator.semconv.service.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 *
 * @deprecated Use {@link ServicePeerAttributesExtractor} instead.
 */
@Deprecated
public final class PeerServiceAttributesExtractor {

  /**
   * Returns a new {@link AttributesExtractor} that will use the passed {@code attributesGetter}
   * instance to determine the value of the {@code peer.service} attribute.
   */
  @SuppressWarnings("deprecation") // using deprecated PeerServiceResolver
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, PeerServiceResolver peerServiceResolver) {
    return ServicePeerAttributesExtractor.create(attributesGetter, peerServiceResolver);
  }

  private PeerServiceAttributesExtractor() {}
}
