/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.service;

import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;

@SuppressWarnings("deprecation") // using deprecated semconv
public class SemconvServiceStabilityUtil {

  public static boolean emitOldServicePeerSemconv() {
    return SemconvStability.emitOldServicePeerSemconv();
  }

  public static boolean emitPreviewServicePeerSemconv() {
    return SemconvStability.emitPreviewServicePeerSemconv();
  }

  /** Returns PEER_SERVICE or SERVICE_PEER_NAME depending on service.peer semconv stability mode. */
  public static AttributeKey<String> maybeStablePeerService() {
    if (emitPreviewServicePeerSemconv()) {
      return SERVICE_PEER_NAME;
    }
    return PEER_SERVICE;
  }

  private SemconvServiceStabilityUtil() {}
}
