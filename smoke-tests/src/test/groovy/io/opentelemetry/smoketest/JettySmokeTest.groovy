/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class JettySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started Server.*")
  }
}

@AppServer(version = "9.4.39", jdk = "8")
class Jetty9Jdk8 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "11")
class Jetty9Jdk11 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "17")
class Jetty9Jdk17 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "18")
class Jetty9Jdk18 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "8-openj9")
class Jetty9Jdk8Openj9 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "11-openj9")
class Jetty9Jdk11Openj9 extends JettySmokeTest {
}

@AppServer(version = "9.4.39", jdk = "16-openj9")
class Jetty9Jdk16Openj9 extends JettySmokeTest {
}

@AppServer(version = "10.0.7", jdk = "11")
class Jetty10Jdk11 extends JettySmokeTest {
}

@AppServer(version = "10.0.7", jdk = "17")
class Jetty10Jdk17 extends JettySmokeTest {
}

@AppServer(version = "10.0.7", jdk = "18")
class Jetty10Jdk18 extends JettySmokeTest {
}

@AppServer(version = "10.0.7", jdk = "11-openj9")
class Jetty10Jdk11Openj9 extends JettySmokeTest {
}

@AppServer(version = "10.0.7", jdk = "16-openj9")
class Jetty10Jdk16Openj9 extends JettySmokeTest {
}

@AppServer(version = "11.0.7", jdk = "11")
class Jetty11Jdk11 extends JettySmokeTest {
}

@AppServer(version = "11.0.7", jdk = "17")
class Jetty11Jdk17 extends JettySmokeTest {
}

@AppServer(version = "11.0.7", jdk = "18")
class Jetty11Jdk18 extends JettySmokeTest {
}

@AppServer(version = "11.0.7", jdk = "11-openj9")
class Jetty11Jdk11Openj9 extends JettySmokeTest {
}

@AppServer(version = "11.0.7", jdk = "16-openj9")
class Jetty11Jdk16Openj9 extends JettySmokeTest {
}