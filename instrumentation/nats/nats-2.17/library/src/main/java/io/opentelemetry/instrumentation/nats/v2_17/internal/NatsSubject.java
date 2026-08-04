/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

final class NatsSubject {
  static final String JETSTREAM_ACK_SUBJECT = "$JS.ACK";

  static boolean isJetStreamAck(String subject) {
    return subject.startsWith(JETSTREAM_ACK_SUBJECT + ".");
  }

  private NatsSubject() {}
}
