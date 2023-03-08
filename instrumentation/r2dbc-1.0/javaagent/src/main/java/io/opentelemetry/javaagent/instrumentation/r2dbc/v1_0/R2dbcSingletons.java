/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.R2dbcInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.R2dbcNetAttributesGetter;

public final class R2dbcSingletons {

  private static final Instrumenter<DbExecution, Void> INSTRUMENTER =
      new R2dbcInstrumenterBuilder(GlobalOpenTelemetry.get())
          .addAttributeExtractor(
              PeerServiceAttributesExtractor.create(
                  R2dbcNetAttributesGetter.INSTANCE, CommonConfig.get().getPeerServiceMapping()))
          .build();

  public static Instrumenter<DbExecution, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private R2dbcSingletons() {}
}
