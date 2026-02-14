/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 *
 * @deprecated Use {@link HttpClientServicePeerAttributesExtractor} instead.
 */
@Deprecated
@SuppressWarnings("deprecation") // uses deprecated PeerServiceResolver
public final class HttpClientPeerServiceAttributesExtractor {

  /**
   * Returns a new {@link AttributesExtractor} that will use the passed {@code attributesGetter} to
   * extract server address and port (with fallback to the HTTP Host header).
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    return HttpClientServicePeerAttributesExtractor.create(
        attributesGetter, ServicePeerResolver.fromPeerServiceResolver(peerServiceResolver));
  }

  private HttpClientPeerServiceAttributesExtractor() {}
}
