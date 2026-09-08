/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.nio.charset.StandardCharsets.US_ASCII;

import javax.annotation.Nullable;

final class NatsSubject {
  static final String JETSTREAM_ACK_SUBJECT = "$JS.ACK";

  static final String ACK_OPERATION_NAME = "ack";
  static final String NAK_OPERATION_NAME = "nak";
  static final String IN_PROGRESS_OPERATION_NAME = "in-progress";
  static final String TERM_OPERATION_NAME = "term";

  static boolean isJetStreamSettlement(String subject) {
    return subject.startsWith(JETSTREAM_ACK_SUBJECT + ".");
  }

  @Nullable
  static String getJetStreamSettlementOperationName(String subject, @Nullable byte[] body) {
    if (!isJetStreamSettlement(subject) || body == null) {
      return null;
    }
    String operation = new String(body, US_ASCII);
    if (operation.startsWith("+ACK")) {
      return ACK_OPERATION_NAME;
    }
    if (operation.startsWith("-NAK")) {
      return NAK_OPERATION_NAME;
    }
    if (operation.startsWith("+WPI")) {
      return IN_PROGRESS_OPERATION_NAME;
    }
    if (operation.startsWith("+TERM")) {
      return TERM_OPERATION_NAME;
    }
    return "settle";
  }

  private NatsSubject() {}
}
