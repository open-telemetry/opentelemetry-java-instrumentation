/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

/** Recognizes messaging Vert.x does for its own purposes, which must not be traced. */
final class EventBusInternals {

  private static final String CREDIT_ADDRESS_SUFFIX = "-credit";
  private static final int UUID_LENGTH = 36;

  /**
   * Vert.x 3 registers the handlers for replies and for producer flow control as regular {@code
   * MessageConsumer} handlers. Both are lambdas declared in these classes, while their addresses
   * differ between versions: a counter, {@code __vertx.reply.<counter>} or a UUID.
   */
  static boolean isReplyHandler(Object handler) {
    return handler.getClass().getName().startsWith("io.vertx.core.eventbus.impl.EventBusImpl");
  }

  /** See {@link #isReplyHandler}. */
  static boolean isFlowControlHandler(Object handler) {
    return handler
        .getClass()
        .getName()
        .startsWith("io.vertx.core.eventbus.impl.MessageProducerImpl");
  }

  /** {@code MessageProducer} flow control messages go to {@code <uuid>-credit} addresses. */
  static boolean isInternalAddress(String address) {
    return address.length() == UUID_LENGTH + CREDIT_ADDRESS_SUFFIX.length()
        && address.endsWith(CREDIT_ADDRESS_SUFFIX)
        && address.charAt(8) == '-'
        && address.charAt(13) == '-';
  }

  private EventBusInternals() {}
}
