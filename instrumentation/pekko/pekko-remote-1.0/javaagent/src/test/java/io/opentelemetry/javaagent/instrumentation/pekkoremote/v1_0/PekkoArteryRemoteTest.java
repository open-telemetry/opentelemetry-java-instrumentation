/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import com.typesafe.config.Config;

class PekkoArteryRemoteTest extends AbstractPekkoRemoteTest {

  @Override
  protected Config remoteConfig() {
    return parseConfig(
        "pekko.remote.artery.transport = tcp\n"
            + "pekko.remote.artery.canonical.hostname = 127.0.0.1\n"
            + "pekko.remote.artery.canonical.port = 0\n");
  }
}
