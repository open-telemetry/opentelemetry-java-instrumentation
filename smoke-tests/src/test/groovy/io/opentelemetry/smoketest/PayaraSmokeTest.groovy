/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class PayaraSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-payara"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return ["HZ_PHONE_HOME_ENABLED": "false"]
  }

  @Override
  protected String getJvmArgsEnvVarName() {
    return "JVM_ARGS"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*(app was successfully deployed|deployed with name app).*")
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

@AppServer(version = "5.2020.6", jdk = "8")
class Payara52020Jdk8 extends PayaraSmokeTest {
}

@AppServer(version = "5.2020.6", jdk = "8-openj9")
class Payara52020Jdk8Openj9 extends PayaraSmokeTest {
}

@AppServer(version = "5.2020.6", jdk = "11")
class Payara52020Jdk11 extends PayaraSmokeTest {
}

@AppServer(version = "5.2020.6", jdk = "11-openj9")
class Payara52020Jdk11Openj9 extends PayaraSmokeTest {
}

@AppServer(version = "5.2021.8", jdk = "8")
class Payara52021Jdk8 extends PayaraSmokeTest {
}

@AppServer(version = "5.2021.8", jdk = "8-openj9")
class Payara52021Jdk8Openj9 extends PayaraSmokeTest {
}

@AppServer(version = "5.2021.8", jdk = "11")
class Payara52021Jdk11 extends PayaraSmokeTest {
}

@AppServer(version = "5.2021.8", jdk = "11-openj9")
class Payara52021Jdk11Openj9 extends PayaraSmokeTest {
}