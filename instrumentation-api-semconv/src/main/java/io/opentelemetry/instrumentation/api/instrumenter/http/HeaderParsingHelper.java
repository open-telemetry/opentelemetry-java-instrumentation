/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor.AddressPortSink;

final class HeaderParsingHelper {

  static boolean notFound(int pos, int end) {
    return pos < 0 || pos >= end;
  }

  static void setPort(AddressPortSink sink, String header, int start, int end) {
    if (start == end) {
      return;
    }
    try {
      sink.setPort(Integer.parseInt(header.substring(start, end)));
    } catch (NumberFormatException ignored) {
      // malformed port, ignoring
    }
  }

  private HeaderParsingHelper() {}
}
