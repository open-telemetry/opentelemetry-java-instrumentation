/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import groovy.transform.PackageScope
import io.lettuce.core.ClientOptions

@PackageScope
final class LettuceTestUtil {

  static final ClientOptions CLIENT_OPTIONS

  static {
    def options = ClientOptions.builder()
    // Disable autoreconnect so we do not get stray traces popping up on server shutdown
      .autoReconnect(false)
    if (Boolean.getBoolean("testLatestDeps")) {
      // Force RESP2 on 6+ for consistency in tests
      options
        .pingBeforeActivateConnection(false)
        .protocolVersion(Class.forName("io.lettuce.core.protocol.ProtocolVersion").getField("RESP2").get(null))
    }
    CLIENT_OPTIONS = options.build()
  }

  private LettuceTestUtil() {}
}
