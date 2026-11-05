/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import io.opentelemetry.context.Context;

/** Holds the context and processing state created for a JMS receive operation. */
public final class JmsReceiveContext {

  private final Context context;
  private final JmsMessageProcessingState processingState;

  public JmsReceiveContext(Context context, JmsMessageProcessingState processingState) {
    this.context = context;
    this.processingState = processingState;
  }

  public Context context() {
    return context;
  }

  public JmsMessageProcessingState processingState() {
    return processingState;
  }
}
