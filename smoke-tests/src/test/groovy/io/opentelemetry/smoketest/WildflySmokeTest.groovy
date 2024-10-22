/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class WildflySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-wildfly"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*started in.*")
  }

}

@AppServer(version = "13.0.0.Final", jdk = "8")
class Wildfly13Jdk8 extends WildflySmokeTest {
}

@AppServer(version = "13.0.0.Final", jdk = "8-openj9")
class Wildfly13Jdk8Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "8")
class Wildfly17Jdk8 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "8-openj9")
class Wildfly17Jdk8Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "11")
class Wildfly17Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "11-openj9")
class Wildfly17Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "17")
class Wildfly17Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "17-openj9")
class Wildfly17Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "21")
class Wildfly17Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "21-openj9")
class Wildfly17Jdk21Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "8")
class Wildfly21Jdk8 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "8-openj9")
class Wildfly21Jdk8Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "11")
class Wildfly21Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "11-openj9")
class Wildfly21Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "17")
class Wildfly21Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "17-openj9")
class Wildfly21Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "21")
class Wildfly21Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "21-openj9")
class Wildfly21Jdk21Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "11")
class Wildfly28Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "11-openj9")
class Wildfly28Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "17")
class Wildfly28Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "17-openj9")
class Wildfly28Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "21")
class Wildfly28Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "21-openj9")
class Wildfly28Jdk21Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "23")
class Wildfly28Jdk23 extends WildflySmokeTest {
}

@AppServer(version = "28.0.1.Final", jdk = "23-openj9")
class Wildfly28Jdk23Openj9 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "11")
class Wildfly29Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "11-openj9")
class Wildfly29Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "17")
class Wildfly29Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "17-openj9")
class Wildfly29Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "21")
class Wildfly29Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "21-openj9")
class Wildfly29Jdk21Openj9 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "23")
class Wildfly29Jdk23 extends WildflySmokeTest {
}

@AppServer(version = "29.0.1.Final", jdk = "23-openj9")
class Wildfly29Jdk23Openj9 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "11")
class Wildfly30Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "11-openj9")
class Wildfly30Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "17")
class Wildfly30Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "17-openj9")
class Wildfly30Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "21")
class Wildfly30Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "21-openj9")
class Wildfly30Jdk21Openj9 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "23")
class Wildfly30Jdk23 extends WildflySmokeTest {
}

@AppServer(version = "30.0.1.Final", jdk = "23-openj9")
class Wildfly30Jdk23Openj9 extends WildflySmokeTest {
}
