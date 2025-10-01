/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class OpenSearchSingletons {
  private static final Instrumenter<OpenSearchRequest, Void> INSTRUMENTER =
      OpenSearchInstrumenterFactory.create("io.opentelemetry.opensearch-java-3.0");

  public static Instrumenter<OpenSearchRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private OpenSearchSingletons() {}
}
