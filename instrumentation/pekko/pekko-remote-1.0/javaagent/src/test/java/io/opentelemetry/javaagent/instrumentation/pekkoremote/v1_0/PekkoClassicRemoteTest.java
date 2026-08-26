/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import com.typesafe.config.Config;

class PekkoClassicRemoteTest extends AbstractPekkoRemoteTest {

  @Override
  protected Config remoteConfig() {
    return parseConfig(
        "pekko.remote.artery.enabled = off\n"
            + "pekko.remote.classic.enabled-transports = [\"pekko.remote.classic.netty.tcp\"]\n"
            + "pekko.remote.classic.netty.tcp.hostname = 127.0.0.1\n"
            + "pekko.remote.classic.netty.tcp.port = 0\n");
  }
}
