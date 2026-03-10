/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.service;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableServicePeerSemconv;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;

import io.opentelemetry.api.common.AttributeKey;

// until old peer.service attribute is dropped in 3.0
@SuppressWarnings("deprecation") // using deprecated semconv
public final class SemconvServiceStabilityUtil {

  /** Returns PEER_SERVICE or SERVICE_PEER_NAME depending on service.peer semconv stability mode. */
  public static AttributeKey<String> maybeStablePeerService() {
    if (emitStableServicePeerSemconv()) {
      return SERVICE_PEER_NAME;
    }
    return PEER_SERVICE;
  }

  private SemconvServiceStabilityUtil() {}
}
