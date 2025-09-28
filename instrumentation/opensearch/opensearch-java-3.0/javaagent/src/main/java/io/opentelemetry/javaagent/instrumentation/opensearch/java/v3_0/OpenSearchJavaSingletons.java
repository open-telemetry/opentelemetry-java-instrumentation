/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class OpenSearchJavaSingletons {
  private static final Instrumenter<OpenSearchJavaRequest, Void> INSTRUMENTER =
      OpenSearchJavaInstrumenterFactory.create("io.opentelemetry.opensearch-java-3.0");

  public static Instrumenter<OpenSearchJavaRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private OpenSearchJavaSingletons() {}
}
