/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

/** Tracks whether a JMS message delivery has been counted. */
public final class JmsMessageDeliveryState {

  private boolean consumedMessagesRecorded;
  private boolean receivePending;
  private boolean processingReceivedMessage;
  private int processingDepth;

  public synchronized void prepareForReceive() {
    consumedMessagesRecorded = false;
    receivePending = true;
  }

  public synchronized boolean beginProcessing() {
    if (processingDepth == 0) {
      processingReceivedMessage = receivePending;
      receivePending = false;
      if (!processingReceivedMessage) {
        consumedMessagesRecorded = false;
      }
    }
    processingDepth++;
    return processingReceivedMessage;
  }

  public synchronized boolean endProcessing() {
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

  public synchronized void prepareForWrapping() {
    if (processingDepth == 0 && !receivePending) {
      consumedMessagesRecorded = false;
    }
  }

  public synchronized boolean claimConsumedMessages() {
    if (consumedMessagesRecorded) {
      return false;
    }
    consumedMessagesRecorded = true;
    return true;
  }
}
