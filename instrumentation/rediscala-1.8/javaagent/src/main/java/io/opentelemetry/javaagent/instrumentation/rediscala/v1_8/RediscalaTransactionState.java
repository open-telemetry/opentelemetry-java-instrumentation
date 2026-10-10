/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

public final class RediscalaTransactionState {

  private final Object client;

  public RediscalaTransactionState(Object client) {
    this.client = client;
  }

  Object getClient() {
    return client;
  }
}
