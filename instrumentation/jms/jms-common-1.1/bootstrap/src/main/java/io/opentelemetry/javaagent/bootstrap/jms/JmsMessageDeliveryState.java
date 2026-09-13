/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks whether a JMS message delivery has been counted. */
public final class JmsMessageDeliveryState {

  private final AtomicBoolean consumedMessagesRecorded = new AtomicBoolean();

  public boolean claimConsumedMessages() {
    return consumedMessagesRecorded.compareAndSet(false, true);
  }
}
