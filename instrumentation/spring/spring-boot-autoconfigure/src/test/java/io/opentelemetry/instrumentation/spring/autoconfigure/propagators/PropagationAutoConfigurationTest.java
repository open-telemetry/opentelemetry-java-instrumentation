package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropagationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, PropagationAutoConfiguration.class));

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName("when propagation is ENABLED should initialize PropagationAutoConfiguration bean")
  void shouldBeConfigured(){

    this.contextRunner
        .withPropertyValues("otel.propagation.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("contextPropagators", ContextPropagators.class)).isNotNull());

  }

  @Test
  @DisplayName("when propagation is DISABLED should NOT initialize PropagationAutoConfiguration bean")
  void shouldNotBeConfigured(){

    this.contextRunner
        .withPropertyValues("otel.propagation.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("contextPropagators")).isFalse());

  }

  @Test
  @DisplayName("when propagation enabled property is MISSING should initialize PropagationAutoConfiguration bean")
  void noProperty() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("contextPropagators", ContextPropagators.class)).isNotNull());
  }

}
