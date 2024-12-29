/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public abstract class AbstractSpringStarterSmokeTest {

  private static final List<String> IGNORED_WARNINGS =
      Arrays.asList(
          "Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider",
          "The architecture 'amd64' for image",
          "The DescribeTopicPartitions API is not supported, using Metadata API to describe topics");

  @Autowired protected OpenTelemetry openTelemetry;

  protected SpringSmokeTestRunner testing;

  @BeforeAll
  static void beforeAll() {
    SpringSmokeTestRunner.resetExporters();
  }

  @BeforeEach
  void setUpTesting() {
    if (openTelemetry != null) {
      // @Autowired doesn't work in all tests, e.g. AbstractJvmKafkaSpringStarterSmokeTest
      // those tests have to manage the testing instance,
      // themselves because they don't use @SpringBootTest
      testing = new SpringSmokeTestRunner(openTelemetry);
    }
  }

  @AfterEach
  void checkSpringLogs(CapturedOutput output) {
    // warnings are emitted if the auto-configuration have non-fatal problems
    assertThat(output)
        // not a warning in Spring Boot 2
        .doesNotContain("is not eligible for getting processed by all BeanPostProcessors")
        // only look for WARN and ERROR log level, e.g. [Test worker] WARN
        .satisfies(
            s -> {
              for (String line : s.toString().split("\n")) {
                if (IGNORED_WARNINGS.stream().noneMatch(line::contains)) {
                  assertThat(line).doesNotContain("] WARN").doesNotContain("] ERROR");
                }
              }
            });
  }

  static SpanDataAssert withSpanAssert(SpanDataAssert span) {
    return span.hasName("SpringComponent.withSpanMethod")
        .hasAttribute(AttributeKey.stringKey("paramName"), "from-controller");
  }
}
