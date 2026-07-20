/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseRequest;

public class HbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0";
  private static final Instrumenter<HbaseRequest, Void> instrumenter =
      HbaseInstrumenterFactory.create(INSTRUMENTATION_NAME);

  public static Instrumenter<HbaseRequest, Void> instrumenter() {
    return instrumenter;
  }

  private HbaseSingletons() {}
}
