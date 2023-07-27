/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.r2dbc.v1_0.R2dbcTelemetry;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.R2dbcNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class R2dbcSingletons {

  private static final R2dbcTelemetry TELEMETRY =
      R2dbcTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(CommonConfig.get().isStatementSanitizationEnabled())
          .addAttributeExtractor(
              PeerServiceAttributesExtractor.create(
                  R2dbcNetAttributesGetter.INSTANCE, CommonConfig.get().getPeerServiceResolver()))
          .build();

  public static R2dbcTelemetry telemetry() {
    return TELEMETRY;
  }

  private R2dbcSingletons() {}
}
