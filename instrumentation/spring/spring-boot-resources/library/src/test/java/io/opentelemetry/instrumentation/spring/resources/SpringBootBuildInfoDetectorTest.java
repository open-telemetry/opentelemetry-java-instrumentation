/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.InputStream;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootBuildInfoDetectorTest {

  static final String BUILD_PROPS = "build-info.properties";
  static final String META_INFO = "META-INF";

  @Mock ConfigProperties config;
  @Mock SystemHelper system;

  private static class TestCase {
    String name;
    private final Function<SystemHelper, SpringBootBuildInfoDetector> factory;
    AttributeKey<String> key;
    String expected;
    InputStream input;

    public TestCase(
        String name,
        Function<SystemHelper, SpringBootBuildInfoDetector> factory,
        AttributeKey<String> key,
        String expected,
        InputStream input) {
      this.name = name;
      this.factory = factory;
      this.key = key;
      this.expected = expected;
      this.input = input;
    }
  }

  @TestFactory
  Collection<DynamicTest> createResource() {
    return Stream.of(
            new TestCase(
                "name ok",
                SpringBootBuildInfoServiceNameDetector::new,
                SERVICE_NAME,
                "some-name",
                openClasspathResource(META_INFO + "/" + BUILD_PROPS)),
            new TestCase(
                "version ok",
                SpringBootServiceVersionDetector::new,
                SERVICE_VERSION,
                "0.0.2",
                openClasspathResource(META_INFO + "/" + BUILD_PROPS)),
            new TestCase(
                "name - no resource",
                SpringBootBuildInfoServiceNameDetector::new,
                SERVICE_NAME,
                null,
                null),
            new TestCase(
                "version - no resource",
                SpringBootServiceVersionDetector::new,
                SERVICE_VERSION,
                null,
                null),
            new TestCase(
                "name - empty resource",
                SpringBootBuildInfoServiceNameDetector::new,
                SERVICE_NAME,
                null,
                openClasspathResource(BUILD_PROPS)),
            new TestCase(
                "version - empty resource",
                SpringBootServiceVersionDetector::new,
                SERVICE_VERSION,
                null,
                openClasspathResource(BUILD_PROPS)))
        .map(
            t ->
                DynamicTest.dynamicTest(
                    t.name,
                    () -> {
                      when(system.openClasspathResource(META_INFO, BUILD_PROPS))
                          .thenReturn(t.input);

                      assertThat(t.factory.apply(system).createResource(config).getAttribute(t.key))
                          .isEqualTo(t.expected);
                    }))
        .collect(Collectors.toList());
  }

  private static InputStream openClasspathResource(String resource) {
    return SpringBootBuildInfoDetectorTest.class.getClassLoader().getResourceAsStream(resource);
  }
}
