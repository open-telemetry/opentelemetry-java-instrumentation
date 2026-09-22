/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

/** Coordinates nested processing observers for one JMS message delivery. */
public final class JmsMessageProcessingState {
  private boolean processingCompleted;
  private int processingDepth;

  /** Returns whether this is the first processing observer for this delivery. */
  public boolean beginProcessing() {
    if (processingCompleted) {
      return false;
    }
    processingDepth++;
    return processingDepth == 1;
  }

  public boolean isProcessingCompleted() {
    return processingCompleted;
  }

  public boolean endProcessing() {
    if (processingDepth == 0) {
      return false;
    }
    processingDepth--;
    if (processingDepth == 0) {
      processingCompleted = true;
      return true;
    }
    return false;
  }
}
