package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclarativeConfigEarlyInitAgentConfigTest {

  @BeforeEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void globalOpenTelemetry() {
    EarlyInitAgentConfig config = EarlyInitAgentConfig.create(); // no declarative config file
    assertTrue(config.isAgentEnabled(), "Agent should be enabled by default");
    AutoConfiguredOpenTelemetrySdk sdk =
        config.installOpenTelemetrySdk(
            DeclarativeConfigEarlyInitAgentConfig.class.getClassLoader());
    assertThat(sdk).isNotNull().isNotEqualTo(OpenTelemetry.noop());
  }
}
