/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;

@AutoValue
public abstract class InfluxDbRequest {

  public static InfluxDbRequest create(
      InetSocketAddress address, String connectionString, String dbName, String sql) {
    return new AutoValue_InfluxDbRequest(address, connectionString, dbName, sql);
  }

  public abstract InetSocketAddress getAddress();

  public abstract String getConnectionString();

  public abstract String getDbName();

  public abstract String getSql();
}
