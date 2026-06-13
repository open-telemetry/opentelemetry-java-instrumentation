/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class InfluxDbQuery {

  public static InfluxDbQuery create(
      String host, int port, @Nullable String namespace, @Nullable String query) {
    return new AutoValue_InfluxDbQuery(host, port, namespace, query);
  }

  public abstract String getHost();

  public abstract int getPort();

  @Nullable
  public abstract String getNamespace();

  @Nullable
  public abstract String getQuery();
}