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

@AppServer(version = "21.0.0.12", jdk = "8")
class Liberty21Jdk8 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.12", jdk = "11")
class Liberty21Jdk11 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.12", jdk = "17")
class Liberty21Jdk17 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.12", jdk = "8-openj9")
class Liberty21Jdk8Openj9 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.12", jdk = "11-openj9")
class Liberty21Jdk11Openj9 extends LibertySmokeTest {
}

@AppServer(version = "21.0.0.12", jdk = "17-openj9")
class Liberty21Jdk17Openj9 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "8")
class Liberty22Jdk8 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "11")
class Liberty22Jdk11 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "17")
class Liberty22Jdk17 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "19")
class Liberty22Jdk19 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "8-openj9")
class Liberty22Jdk8Openj9 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "11-openj9")
class Liberty22Jdk11Openj9 extends LibertySmokeTest {
}

@AppServer(version = "22.0.0.12", jdk = "17-openj9")
class Liberty22Jdk17Openj9 extends LibertySmokeTest {
}
    
@AppServer(version = "23.0.0.3", jdk = "8")
class Liberty23Jdk8 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "11")
class Liberty23Jdk11 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "17")
class Liberty23Jdk17 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "19")
class Liberty23Jdk19 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "8-openj9")
class Liberty23Jdk8Openj9 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "11-openj9")
class Liberty23Jdk11Openj9 extends LibertySmokeTest {
}

@AppServer(version = "23.0.0.3", jdk = "17-openj9")
class Liberty23Jdk17Openj9 extends LibertySmokeTest {
}
