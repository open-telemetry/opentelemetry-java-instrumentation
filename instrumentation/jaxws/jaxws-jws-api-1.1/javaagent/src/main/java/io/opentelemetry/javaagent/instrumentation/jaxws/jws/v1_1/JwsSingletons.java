/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;

public final class JwsSingletons {

  private static final Instrumenter<JaxWsRequest, Void> INSTANCE =
      JaxWsInstrumenterFactory.createInstrumenter("io.opentelemetry.jws-1.1");

  public static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return INSTANCE;
  }

  private JwsSingletons() {}
}
