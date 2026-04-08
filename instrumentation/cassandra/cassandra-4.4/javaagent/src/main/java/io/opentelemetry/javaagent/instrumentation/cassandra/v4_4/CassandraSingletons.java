/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.cassandra.v4_4.CassandraTelemetry;

final class CassandraSingletons {

  static final CassandraTelemetry telemetry =
      CassandraTelemetry.builder(GlobalOpenTelemetry.get())
          .setQuerySanitizationEnabled(
              DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "cassandra"))
          .build();

  private CassandraSingletons() {}
}
