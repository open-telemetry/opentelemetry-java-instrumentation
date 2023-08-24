/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface CarrierEnricher {
  /**
   * Creates a new carrier from the carrier passed as parameter and perform any necessary
   * enrichment.
   *
   * @param carrier The carrier to enrich from. If null, is passed the enrichment operation will
   *     still be tried.
   * @return the new enriched carrier.
   */
  public Map<String, String> enrichFrom(Map<String, String> carrier);
}
