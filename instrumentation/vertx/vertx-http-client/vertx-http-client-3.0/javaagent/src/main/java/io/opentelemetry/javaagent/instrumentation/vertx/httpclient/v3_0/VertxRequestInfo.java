/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v3_0;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VertxRequestInfo {

  public static VertxRequestInfo create(boolean ssl, String host, int port) {
    return new AutoValue_VertxRequestInfo(ssl, host, port);
  }

  public abstract boolean isSsl();

  public abstract String getHost();

  public abstract int getPort();
}
