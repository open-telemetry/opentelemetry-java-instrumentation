/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

@AppServer(version = "20.0.0.12", jdk = "8")
@AppServer(version = "20.0.0.12", jdk = "8-openj9")
@AppServer(version = "20.0.0.12", jdk = "11")
@AppServer(version = "20.0.0.12", jdk = "11-openj9")
@AppServer(version = "20.0.0.12", jdk = "17")
@AppServer(version = "20.0.0.12", jdk = "16-openj9")
class LibertySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/java-test-containers:liberty"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*server is ready to run a smarter planet.*")
  }
}
