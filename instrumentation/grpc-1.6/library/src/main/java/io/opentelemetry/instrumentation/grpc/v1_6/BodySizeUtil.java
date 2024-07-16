/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import com.google.protobuf.MessageLite;

final class BodySizeUtil {

  static <T> int getBodySize(T message) {
    if (message instanceof MessageLite) {
      return ((MessageLite) message).getSerializedSize();
    } else {
      // Message is not a protobuf message
      return 0;
    }
  }

  private BodySizeUtil() {}
}
