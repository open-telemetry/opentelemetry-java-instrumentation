/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class InfluxDbRequest {

  public static InfluxDbRequest create(
      String host,
      int port,
      String namespace,
      @Nullable String operationName,
      @Nullable String sql) {
    return new AutoValue_InfluxDbRequest(host, port, namespace, operationName, sql);
  }

  public abstract String getHost();

  public abstract int getPort();

  public abstract String getNamespace();

  @Nullable
  public abstract String getOperationName();

  @Nullable
  public abstract String getSql();
}
