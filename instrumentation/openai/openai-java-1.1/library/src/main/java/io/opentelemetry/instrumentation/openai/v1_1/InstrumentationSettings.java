/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

final class InstrumentationSettings {

  final boolean captureMessageContent;

  // we do not directly have access to the client baseUrl after construction, therefore we need to
  // remember it
  // visible for testing
  final String serverAddress;
  final Long serverPort;

  InstrumentationSettings(boolean captureMessageContent, String serverAddress, Long serverPort) {
    this.captureMessageContent = captureMessageContent;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }
}
