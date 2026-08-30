/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import javax.annotation.Nullable;

/**
 * Marks a client whose target is only known once a connection is established, because its connect
 * options come from a supplier that runs per connection attempt. Such a client never has client
 * wide data, so {@link #get()} always returns {@code null} and callers wait for the data attached
 * to the connection instead.
 */
public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  @Override
  @Nullable
  public VertxSqlClientData get() {
    return null;
  }
}
