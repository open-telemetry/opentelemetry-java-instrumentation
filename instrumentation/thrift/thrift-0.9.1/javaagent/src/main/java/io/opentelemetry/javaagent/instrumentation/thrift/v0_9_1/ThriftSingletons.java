/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.common.ThriftInstrumenterFactory;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;

public final class ThriftSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.thrift-0.9.1";

  private static final Instrumenter<ThriftRequest, Integer> CLIENT_INSTRUMENTER =
      ThriftInstrumenterFactory.clientInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<ThriftRequest, Integer> SERVER_INSTRUMENTER =
      ThriftInstrumenterFactory.serverInstrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<ThriftRequest, Integer> clientInstrumenter() {
    return CLIENT_INSTRUMENTER;
  }

  public static Instrumenter<ThriftRequest, Integer> serverInstrumenter() {
    return SERVER_INSTRUMENTER;
  }

  private ThriftSingletons() {}
}
