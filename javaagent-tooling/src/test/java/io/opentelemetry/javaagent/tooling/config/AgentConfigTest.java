package io.opentelemetry.javaagent.tooling.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class AgentConfigTest {

  @ParameterizedTest
  @ArgumentsSource(AgentDebugParams.class)
  void shouldCheckIfAgentDebugModeIsEnabled(String propertyValue, boolean expected) {
    Config config = Config.builder().addProperty("otel.javaagent.debug", propertyValue).build();
    AgentConfig agentConfig = new AgentConfig(config);

    assertEquals(expected, agentConfig.isDebugModeEnabled());
  }

  private static class AgentDebugParams implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("true", true), Arguments.of("blather", false), Arguments.of(null, false));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(InstrumentationEnabledParams.class)
  void shouldCheckIfInstrumentationIsEnabled(
      List<String> names, boolean defaultEnabled, boolean expected) {
    Config config =
        Config.builder()
            .addProperty("otel.instrumentation.order.enabled", "true")
            .addProperty("otel.instrumentation.test-prop.enabled", "true")
            .addProperty("otel.instrumentation.disabled-prop.enabled", "false")
            .addProperty("otel.instrumentation.test-env.enabled", "true")
            .addProperty("otel.instrumentation.disabled-env.enabled", "false")
            .build();
    AgentConfig agentConfig = new AgentConfig(config);

    assertEquals(
        expected, agentConfig.isInstrumentationEnabled(new TreeSet<>(names), defaultEnabled));
  }

  private static class InstrumentationEnabledParams implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(emptyList(), true, true),
          Arguments.of(emptyList(), false, false),
          Arguments.of(singletonList("invalid"), true, true),
          Arguments.of(singletonList("invalid"), false, false),
          Arguments.of(singletonList("test-prop"), false, true),
          Arguments.of(singletonList("test-env"), false, true),
          Arguments.of(singletonList("disabled-prop"), true, false),
          Arguments.of(singletonList("disabled-env"), true, false),
          Arguments.of(asList("other", "test-prop"), false, true),
          Arguments.of(asList("other", "test-env"), false, true),
          Arguments.of(singletonList("order"), false, true),
          Arguments.of(asList("test-prop", "disabled-prop"), false, true),
          Arguments.of(asList("disabled-env", "test-env"), false, true),
          Arguments.of(asList("test-prop", "disabled-prop"), true, false),
          Arguments.of(asList("disabled-env", "test-env"), true, false));
    }
  }
}
