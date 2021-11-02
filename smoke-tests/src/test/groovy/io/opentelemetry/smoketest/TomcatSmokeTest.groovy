/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

abstract class TomcatSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomcat"
  }
}

@AppServer(version = "7.0.109", jdk = "8")
class Tomcat7Jdk8 extends TomcatSmokeTest {
}
@AppServer(version = "7.0.109", jdk = "8-openj9")
class Tomcat7Jdk8Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "8.5.71", jdk = "8")
class Tomcat8Jdk8 extends TomcatSmokeTest {
}
@AppServer(version = "8.5.71", jdk = "11")
class Tomcat8Jdk11 extends TomcatSmokeTest {
}
@AppServer(version = "8.5.71", jdk = "17")
class Tomcat8Jdk17 extends TomcatSmokeTest {
}
@AppServer(version = "8.5.71", jdk = "8-openj9")
class Tomcat8Jdk8Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "8.5.71", jdk = "11-openj9")
class Tomcat8Jdk11Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "9.0.53", jdk = "8")
class Tomcat9Jdk8 extends TomcatSmokeTest {
}
@AppServer(version = "9.0.53", jdk = "11")
class Tomcat9Jdk11 extends TomcatSmokeTest {
}
@AppServer(version = "9.0.53", jdk = "17")
class Tomcat9Jdk17 extends TomcatSmokeTest {
}
@AppServer(version = "9.0.53", jdk = "8-openj9")
class Tomcat9Jdk8Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "9.0.53", jdk = "11-openj9")
class Tomcat9Jdk11Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "10.0.11", jdk = "8")
class Tomcat10Jdk8 extends TomcatSmokeTest {
}
@AppServer(version = "10.0.11", jdk = "11")
class Tomcat10Jdk11 extends TomcatSmokeTest {
}
@AppServer(version = "10.0.11", jdk = "17")
class Tomcat10Jdk17 extends TomcatSmokeTest {
}
@AppServer(version = "10.0.11", jdk = "8-openj9")
class Tomcat10Jdk8Openj9 extends TomcatSmokeTest {
}
@AppServer(version = "10.0.11", jdk = "11-openj9")
class Tomcat10Jdk11Openj9 extends TomcatSmokeTest {
}