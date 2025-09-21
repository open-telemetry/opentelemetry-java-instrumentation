/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.AppServer;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class PayaraSmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      builder("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-payara")
          .waitStrategy(
              new TargetWaitStrategy.Log(
                  Duration.ofMinutes(3),
                  ".*(app was successfully deployed|deployed with name app).*"))
          .jvmArgsEnvVarName("JVM_ARGS")
          .env("HZ_PHONE_HOME_ENABLED", "false")
          .build();

  @Override
  protected SmokeTestInstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected String getSpanName(String path) {
    if ("/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless".equals(path)) {
      return "GET /*";
    }
    return super.getSpanName(path);
  }

  @AppServer(version = "5.2020.6", jdk = "8")
  public static class Payara52020Jdk8 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "8-openj9")
  public static class Payara52020Jdk8Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "11")
  public static class Payara52020Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "5.2020.6", jdk = "11-openj9")
  public static class Payara52020Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "8")
  public static class Payara52021Jdk8 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "8-openj9")
  public static class Payara52021Jdk8Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "11")
  public static class Payara52021Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "5.2021.8", jdk = "11-openj9")
  public static class Payara52021Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "11")
  public static class Payara6Jdk11 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "11-openj9")
  public static class Payara6Jdk11Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "17")
  public static class Payara6Jdk17 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "17-openj9")
  public static class Payara6Jdk17Openj9 extends PayaraSmokeTest {}

  @AppServer(version = "6.2023.12", jdk = "21-openj9")
  public static class Payara6Jdk21Openj9 extends PayaraSmokeTest {}
}
