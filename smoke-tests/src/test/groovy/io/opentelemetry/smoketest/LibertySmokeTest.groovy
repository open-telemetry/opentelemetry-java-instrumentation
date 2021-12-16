/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class LibertySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-liberty"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*server is ready to run a smarter planet.*")
  }
}

@AppServer(version = "20.0.0.12", jdk = "8")
class Liberty20Jdk8 extends LibertySmokeTest {
}

@AppServer(version = "20.0.0.12", jdk = "8-openj9")
class Liberty20Jdk8Openj9 extends LibertySmokeTest {
}

@AppServer(version = "20.0.0.12", jdk = "11")
class Liberty20Jdk11 extends LibertySmokeTest {
}

@AppServer(version = "20.0.0.12", jdk = "11-openj9")
class Liberty20Jdk11Openj9 extends LibertySmokeTest {
}

@AppServer(version = "20.0.0.12", jdk = "16")
class Liberty20Jdk16 extends LibertySmokeTest {
}

@AppServer(version = "20.0.0.12", jdk = "16-openj9")
class Liberty20Jdk16Openj9 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "8")
class Liberty21Jdk8 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "8-openj9")
class Liberty21Jdk8Openj9 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "11")
class Liberty21Jdk11 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "11-openj9")
class Liberty21Jdk11Openj9 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "17")
class Liberty21Jdk17 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "18")
class Liberty21Jdk18 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.10", jdk = "16-openj9")
class Liberty21Jdk16Openj9 extends LibertySmokeTest {
}
