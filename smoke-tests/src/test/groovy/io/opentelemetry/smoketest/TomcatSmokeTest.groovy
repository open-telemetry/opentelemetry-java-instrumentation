/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class TomcatSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomcat"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server startup in.*")
  }

  @Override
  protected boolean expectServerSpan() {
    return this.serverVersion != "7.0.109"
  }
}

@AppServer(version = "7.0.109", jdk = "8")
class Tomcat7Jdk8 extends TomcatSmokeTest {
}

@AppServer(version = "7.0.109", jdk = "8-openj9")
class Tomcat7Jdk8Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "8")
class Tomcat8Jdk8 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "8-openj9")
class Tomcat8Jdk8Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "11")
class Tomcat8Jdk11 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "11-openj9")
class Tomcat8Jdk11Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "17")
class Tomcat8Jdk17 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "17-openj9")
class Tomcat8Jdk17Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "21")
class Tomcat8Jdk21 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "21-openj9")
class Tomcat8Jdk21Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "23")
class Tomcat8Jdk23 extends TomcatSmokeTest {
}

@AppServer(version = "8.5.98", jdk = "23-openj9")
class Tomcat8Jdk23Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "8")
class Tomcat9Jdk8 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "8-openj9")
class Tomcat9Jdk8Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "11")
class Tomcat9Jdk11 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "11-openj9")
class Tomcat9Jdk11Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "17")
class Tomcat9Jdk17 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "17-openj9")
class Tomcat9Jdk17Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "21")
class Tomcat9Jdk21 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "21-openj9")
class Tomcat9Jdk21Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "23")
class Tomcat9Jdk23 extends TomcatSmokeTest {
}

@AppServer(version = "9.0.85", jdk = "23-openj9")
class Tomcat9Jdk23Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "11")
class Tomcat10Jdk11 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "11-openj9")
class Tomcat10Jdk11Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "17")
class Tomcat10Jdk17 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "17-openj9")
class Tomcat10Jdk17Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "21")
class Tomcat10Jdk21 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "21-openj9")
class Tomcat10Jdk21Openj9 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "23")
class Tomcat10Jdk23 extends TomcatSmokeTest {
}

@AppServer(version = "10.1.18", jdk = "23-openj9")
class Tomcat10Jdk23Openj9 extends TomcatSmokeTest {
}
