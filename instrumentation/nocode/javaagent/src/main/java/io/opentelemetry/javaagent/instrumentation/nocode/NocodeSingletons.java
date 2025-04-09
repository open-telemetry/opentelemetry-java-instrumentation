/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class NocodeSingletons {
  private static final Instrumenter<NocodeMethodInvocation, Object> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<NocodeMethodInvocation, Object>builder(
                GlobalOpenTelemetry.get(), "io.opentelemetry.nocode", new NocodeSpanNameExtractor())
            .addAttributesExtractor(new NocodeAttributesExtractor())
            .setSpanStatusExtractor(new NocodeSpanStatusExtractor())
            .buildInstrumenter(new NocodeSpanKindExtractor());
  }

  public static Instrumenter<NocodeMethodInvocation, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private NocodeSingletons() {}
}
