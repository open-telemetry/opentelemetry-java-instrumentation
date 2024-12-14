package io.opentelemetry.instrumentation.awssdk.v2_2;

class Aws2SqsSuppressReceiveSpansW3CPropagatorTest extends Aws2SqsSuppressReceiveSpansTest {
  @Override
  void configure(AwsSdkTelemetryBuilder telemetryBuilder) {
    telemetryBuilder.setUseConfiguredPropagatorForMessaging(isSqsAttributeInjectionEnabled()) // Difference to main test
        .setUseXrayPropagator(isXrayInjectionEnabled()); // Disable to confirm messaging propagator actually works
  }

  @Override
  boolean isSqsAttributeInjectionEnabled() {
    return true;
  }

  @Override
  boolean isXrayInjectionEnabled() {
    return false;
  }
}
