/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import io.opentelemetry.context.Context;

/** Holds the context created for a JMS receive operation. */
public final class JmsReceiveContext {

  private final Context context;

  public JmsReceiveContext(Context context) {
    this.context = context;
  }

  public Context context() {
    return context;
  }
}
