/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
class AwsXrayEnvCarrierEnricherTest {
  @SystemStub final EnvironmentVariables environmentVariables = new EnvironmentVariables();
  @SystemStub final SystemProperties systemProperties = new SystemProperties();

  private static final String XRAY_ENV_VAR = "_X_AMZN_TRACE_ID";
  private static final String XRAY_SYSTEM_PROPERTY = "com.amazonaws.xray.traceHeader";
  private static final String XRAY_PROPAGATING_KEY = "x-amzn-trace-id";

  private static final String TEST_VALUE1 =
      "Root=1-8a3c60f7-d188f8fa79d48a391a778fa6;Parent=0000000000000456;Sampled=0";
  private static final String TEST_VALUE2 =
      "Root=1-8a3c60f7-d188f8fa79d48a391a778fa6;Parent=0000000000000789;Sampled=0";

  private static final AwsXrayEnvCarrierEnricher enricher = new AwsXrayEnvCarrierEnricher();

  @Test
  void enrichFromEnvVar() {
    Map<String, String> carrier = ImmutableMap.of("other-key", "value");
    environmentVariables.set(XRAY_ENV_VAR, TEST_VALUE1);
    systemProperties.set(XRAY_SYSTEM_PROPERTY, "");

    Map<String, String> newCarrier = enricher.enrichFrom(carrier);

    assertThat(carrier.get("other-key")).isEqualTo(newCarrier.get("other-key"));
    assertThat(newCarrier.get(XRAY_PROPAGATING_KEY)).isEqualTo(TEST_VALUE1);
  }

  @Test
  void enrichFromNullCarrier() {
    environmentVariables.set(XRAY_ENV_VAR, TEST_VALUE1);
    systemProperties.set(XRAY_SYSTEM_PROPERTY, "");

    Map<String, String> newCarrier = enricher.enrichFrom(null);

    assertThat(newCarrier.get(XRAY_PROPAGATING_KEY)).isEqualTo(TEST_VALUE1);
  }

  @Test
  void systemPropertyHasPriority() {
    Map<String, String> carrier = ImmutableMap.of("other-key", "value");
    environmentVariables.set(XRAY_ENV_VAR, TEST_VALUE1);
    systemProperties.set(XRAY_SYSTEM_PROPERTY, TEST_VALUE2);

    Map<String, String> newCarrier = enricher.enrichFrom(carrier);

    assertThat(carrier.get("other-key")).isEqualTo(newCarrier.get("other-key"));
    assertThat(newCarrier.get(XRAY_PROPAGATING_KEY)).isEqualTo(TEST_VALUE2);
  }

  @Test
  void noKeySet() {
    Map<String, String> carrier = ImmutableMap.of("other-key", "value");
    environmentVariables.set(XRAY_ENV_VAR, "");
    systemProperties.set(XRAY_SYSTEM_PROPERTY, "");

    Map<String, String> newCarrier = enricher.enrichFrom(carrier);

    assertThat(carrier.get("other-key")).isEqualTo(newCarrier.get("other-key"));
    assertThat(newCarrier.get(XRAY_PROPAGATING_KEY)).isNull();
  }
}
