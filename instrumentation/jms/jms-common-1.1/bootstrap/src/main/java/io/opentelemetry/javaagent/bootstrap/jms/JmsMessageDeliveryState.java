/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks whether a JMS message delivery has been counted. */
public final class JmsMessageDeliveryState {

  private final AtomicBoolean consumedMessagesRecorded = new AtomicBoolean();
  private boolean receivePending;
  private boolean processingReceivedMessage;
  private int processingDepth;

  public void prepareForReceive() {
    consumedMessagesRecorded.set(false);
    receivePending = true;
  }

  public boolean beginProcessing() {
    if (processingDepth == 0) {
      processingReceivedMessage = receivePending;
      receivePending = false;
      if (!processingReceivedMessage) {
        consumedMessagesRecorded.set(false);
      }
    }
    processingDepth++;
    return processingReceivedMessage;
  }

  public boolean endProcessing() {
    if (processingDepth == 0) {
      return false;
    }
    processingDepth--;
    if (processingDepth != 0) {
      return false;
    }
    processingReceivedMessage = false;
    return true;
  }

  public void prepareForWrapping() {
    if (processingDepth == 0 && !receivePending) {
      consumedMessagesRecorded.set(false);
    }
  }

  public boolean claimConsumedMessages() {
    return consumedMessagesRecorded.compareAndSet(false, true);
  }
}
