/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import javax.annotation.Nullable;

public final class RediscalaTransactionState {

  private final Object client;
  @Nullable private final ServerEndpoint endpoint;

  public RediscalaTransactionState(Object client, @Nullable ServerEndpoint endpoint) {
    this.client = client;
    this.endpoint = endpoint;
  }

  Object getClient() {
    return client;
  }

  @Nullable
  ServerEndpoint getEndpoint() {
    return endpoint;
  }
}
