/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class PayaraSmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-payara"))
        .waitStrategy(
            new TargetWaitStrategy.Log(
                Duration.ofMinutes(3),
                ".*(app was successfully deployed|deployed with name app).*"))
        .jvmArgsEnvVarName("JVM_ARGS")
        .env("HZ_PHONE_HOME_ENABLED", "false");
  }

  @Override
  protected String getSpanName(String path) {
    if ("/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless".equals(path)) {
      return "GET /*";
    }
    return super.getSpanName(path);
  }

  @AppServer(version = "5.2020.6", jdk = "8")
  static class Payara52020Jdk8 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "8-openj9")
  static class Payara52020Jdk8Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "11")
  static class Payara52020Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "11-openj9")
  static class Payara52020Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "8")
  static class Payara52021Jdk8 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "8-openj9")
  static class Payara52021Jdk8Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "11")
  static class Payara52021Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "11-openj9")
  static class Payara52021Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "11")
  static class Payara6Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "11-openj9")
  static class Payara6Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "17")
  static class Payara6Jdk17 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "17-openj9")
  static class Payara6Jdk17Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "21-openj9")
  static class Payara6Jdk21Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "25-openj9")
  static class Payara6Jdk25Openj9 extends PayaraSmokeTest {}
}
