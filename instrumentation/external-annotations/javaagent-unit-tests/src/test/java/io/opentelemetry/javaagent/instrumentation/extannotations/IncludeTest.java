package io.opentelemetry.javaagent.instrumentation.extannotations;

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncludeTest {

  @Mock InstrumentationConfig config;

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testConfiguration(String value, String expected) {
    when(config.getString("otel.instrumentation.external-annotations.include")).thenReturn(value);



  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("AppOptics"),
        Arguments.of("Datadog"),
        Arguments.of("Dropwizard"),
        Arguments.of("KamonOld"),
        Arguments.of("KamonNew"),
        Arguments.of("NewRelic"),
        Arguments.of("SignalFx"),
        Arguments.of("Sleuth"),
        Arguments.of("Tracelytics"));
  }
}



}
