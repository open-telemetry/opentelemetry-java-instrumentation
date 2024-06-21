/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
import java.util.Optional;

public final class LibertyDispatcherSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-dispatcher-20.0";

  private static final Instrumenter<LibertyRequest, LibertyResponse> INSTRUMENTER;

  static {
    INSTRUMENTER = JavaagentHttpServerInstrumenterBuilder.create(
        INSTRUMENTATION_NAME, new LibertyDispatcherHttpAttributesGetter(),
        Optional.of(LibertyDispatcherRequestGetter.INSTANCE)
    );
  }

  public static Instrumenter<LibertyRequest, LibertyResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private LibertyDispatcherSingletons() {}
}
