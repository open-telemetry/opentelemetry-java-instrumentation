/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

public class MessageDestination {
  public static final MessageDestination UNKNOWN =
      new MessageDestination("unknown", "unknown", false);

  public final String destinationName;
  public final String destinationKind;
  public final boolean temporary;

  public MessageDestination(String destinationName, String destinationKind, boolean temporary) {
    this.destinationName = destinationName;
    this.destinationKind = destinationKind;
    this.temporary = temporary;
  }
}
