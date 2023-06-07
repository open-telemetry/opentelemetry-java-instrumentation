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

@AppServer(version = "17.0.1.Final", jdk = "11")
class Wildfly17Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "17")
class Wildfly17Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "20")
class Wildfly17Jdk20 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "21")
class Wildfly17Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "8")
class Wildfly21Jdk8 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "11")
class Wildfly21Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "17")
class Wildfly21Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "20")
class Wildfly21Jdk20 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "21")
class Wildfly21Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "11")
class Wildfly28Jdk11 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "17")
class Wildfly28Jdk17 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "20")
class Wildfly28Jdk20 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "21")
class Wildfly28Jdk21 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "8-openj9")
class Wildfly17Jdk8Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "11-openj9")
class Wildfly17Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "17-openj9")
class Wildfly17Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "17.0.1.Final", jdk = "18-openj9")
class Wildfly17Jdk18Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "8-openj9")
class Wildfly21Jdk8Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "11-openj9")
class Wildfly21Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "17-openj9")
class Wildfly21Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "21.0.0.Final", jdk = "18-openj9")
class Wildfly21Jdk18Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "11-openj9")
class Wildfly28Jdk11Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "17-openj9")
class Wildfly28Jdk17Openj9 extends WildflySmokeTest {
}

@AppServer(version = "28.0.0.Final", jdk = "18-openj9")
class Wildfly28Jdk18Openj9 extends WildflySmokeTest {
}
