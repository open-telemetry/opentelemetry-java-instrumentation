/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;

public final class JaxWsSingletons {

  private static final Instrumenter<JaxWsRequest, Void> INSTANCE =
      JaxWsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxws-2.0");

  public static Instrumenter<JaxWsRequest, Void> instrumenter() {
    return INSTANCE;
  }

  private JaxWsSingletons() {}
}
