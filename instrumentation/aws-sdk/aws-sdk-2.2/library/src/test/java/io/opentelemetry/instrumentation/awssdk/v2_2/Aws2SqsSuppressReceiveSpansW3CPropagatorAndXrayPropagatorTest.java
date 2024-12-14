package io.opentelemetry.instrumentation.awssdk.v2_2;

import org.junit.jupiter.api.BeforeAll;

/** We want to test the combination of W3C + Xray, as that's what you'll get in prod if you enable W3C. */
class Aws2SqsSuppressReceiveSpansW3CPropagatorAndXrayPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {

  @BeforeAll
  static void setup() {
    AwsSdkTelemetryBuilder telemetryBuilder = AwsSdkTelemetry.builder(testing.getOpenTelemetry())
        .setCaptureExperimentalSpanAttributes(true);
    configure(telemetryBuilder);
    telemetry = telemetryBuilder.build();
  }

  static void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()); // Difference to main test
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    return true;
  }
}
