/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class InfluxDbRequest {

  public static InfluxDbRequest create(String host, Integer port, String dbName, String sql) {
    return new AutoValue_InfluxDbRequest(host, port, dbName, sql);
  }

  public abstract String getHost();

  public abstract Integer getPort();

  public abstract String getDbName();

  public abstract String getSql();
}
