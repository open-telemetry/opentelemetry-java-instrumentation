/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.v2_0.JaxWsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.v2_0.JaxWsRequest;

class JaxWsSingletons {

  private static final Instrumenter<JaxWsRequest, Void> instrumenter =
      JaxWsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxws-2.0");

  static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return instrumenter;
  }

  private JaxWsSingletons() {}
}
