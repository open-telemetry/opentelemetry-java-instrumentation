/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class InfluxDbOperation {

  public static InfluxDbOperation create(
      String host,
      int port,
      @Nullable String namespace,
      @Nullable String operation,
      @Nullable Long batchSize) {
    return new AutoValue_InfluxDbOperation(host, port, namespace, operation, batchSize);
  }

  public abstract String getHost();

  public abstract int getPort();

  @Nullable
  public abstract String getNamespace();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract Long getBatchSize();
}
