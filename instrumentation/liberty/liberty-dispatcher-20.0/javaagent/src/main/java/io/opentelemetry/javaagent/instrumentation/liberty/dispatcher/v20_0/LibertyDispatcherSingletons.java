/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.v20_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;

class LibertyDispatcherSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-dispatcher-20.0";

  private static final Instrumenter<LibertyRequest, LibertyResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new LibertyDispatcherHttpAttributesGetter(),
            new LibertyDispatcherRequestGetter());
  }

  static Instrumenter<LibertyRequest, LibertyResponse> instrumenter() {
    return instrumenter;
  }

  private LibertyDispatcherSingletons() {}
}
