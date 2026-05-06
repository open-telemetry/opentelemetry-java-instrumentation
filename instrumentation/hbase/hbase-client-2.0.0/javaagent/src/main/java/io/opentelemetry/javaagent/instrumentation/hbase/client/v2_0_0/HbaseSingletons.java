/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hbase.common.HbaseInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hbase.common.HbaseRequest;
import io.opentelemetry.javaagent.instrumentation.hbase.common.RequestAndContext;

public class HbaseSingletons {

  public static final ThreadLocal<String> TABLE_THREAD_LOCAL = new ThreadLocal<>();
  public static final ThreadLocal<RequestAndContext> RC_THREAD_LOCAL = new ThreadLocal<>();
  public static final VirtualField<Object, RequestAndContext> REQUEST_AND_CONTEXT =
      VirtualField.find(Object.class, RequestAndContext.class);

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0.0";
  private static final Instrumenter<HbaseRequest, Void> INSTRUMENTER =
      HbaseInstrumenterFactory.instrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<HbaseRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private HbaseSingletons() {}
}
