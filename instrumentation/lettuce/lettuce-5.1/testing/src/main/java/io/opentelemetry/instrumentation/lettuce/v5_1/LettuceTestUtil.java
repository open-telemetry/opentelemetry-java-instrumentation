/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;

final class LettuceTestUtil {
  static final ClientOptions CLIENT_OPTIONS;

  static {
    ClientOptions.Builder options =
        ClientOptions.builder()
            // Disable autoreconnect so we do not get stray traces popping up on server shutdown
            .autoReconnect(false);
    if (Boolean.getBoolean("testLatestDeps")) {
      // Force RESP2 on 6+ for consistency in tests
      options.pingBeforeActivateConnection(false).protocolVersion(ProtocolVersion.RESP2);
    }
    CLIENT_OPTIONS = options.build();
  }

  private LettuceTestUtil() {}
}
