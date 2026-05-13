/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.api.v1_1;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.v2_0.JaxWsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.v2_0.JaxWsRequest;

class JwsSingletons {

  private static final Instrumenter<JaxWsRequest, Void> instrumenter =
      JaxWsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxws-jws-api-1.1");

  static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return instrumenter;
  }

  private JwsSingletons() {}
}
