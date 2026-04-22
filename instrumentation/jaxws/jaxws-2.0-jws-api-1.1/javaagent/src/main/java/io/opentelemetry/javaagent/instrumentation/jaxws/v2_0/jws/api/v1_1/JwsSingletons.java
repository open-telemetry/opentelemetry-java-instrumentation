/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.jws.api.v1_1;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;

class JwsSingletons {

  private static final Instrumenter<JaxWsRequest, Void> instrumenter =
      JaxWsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxws-2.0-jws-api-1.1");

  static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return instrumenter;
  }

  private JwsSingletons() {}
}
