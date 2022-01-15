/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class TomeeSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomee"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*Server startup in.*")
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless":
        return "/*"
    }
    return super.getSpanName(path)
  }
}

@AppServer(version = "7.0.9", jdk = "8")
class Tomee70Jdk8 extends TomeeSmokeTest {
}

@AppServer(version = "7.0.9", jdk = "8-openj9")
class Tomee70Jdk8Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "7.1.4", jdk = "8")
class Tomee71Jdk8 extends TomeeSmokeTest {
}

@AppServer(version = "7.1.4", jdk = "8-openj9")
class Tomee71Jdk8Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "8")
class Tomee8Jdk8 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "11")
class Tomee8Jdk11 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "17")
class Tomee8Jdk17 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "18")
class Tomee8Jdk18 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "8-openj9")
class Tomee8Jdk8Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "11-openj9")
class Tomee8Jdk11Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "8.0.8", jdk = "16-openj9")
class Tomee8Jdk16Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "8")
class Tomee9Jdk8 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "11")
class Tomee9Jdk11 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "17")
class Tomee9Jdk17 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "18")
class Tomee9Jdk18 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "8-openj9")
class Tomee9Jdk8Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "11-openj9")
class Tomee9Jdk11Openj9 extends TomeeSmokeTest {
}

@AppServer(version = "9.0.0-M7", jdk = "16-openj9")
class Tomee9Jdk16Openj9 extends TomeeSmokeTest {
}