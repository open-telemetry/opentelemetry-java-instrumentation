/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_CHECKSUM;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_CHECKSUM_ALGORITHM;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_DESCRIPTION;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_NAME;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_PATH;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_TYPE;
import static io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8.JarAnalyzer.PACKAGE_VERSION;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.assertj.AttributesAssert;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpRequest;

class JarAnalyzerTest {

  @ParameterizedTest
  @MethodSource("processUrlArguments")
  void processUrl_EmitsEvents(URL archiveUrl, Consumer<AttributesAssert> attributesConsumer) {
    ExtendedLogRecordBuilder builder = mock(ExtendedLogRecordBuilder.class);
    when(builder.setEventName(eq("package.info"))).thenReturn(builder);
    when(builder.setAllAttributes(any())).thenReturn(builder);

    JarAnalyzer.processUrl(builder, archiveUrl);

    ArgumentCaptor<Attributes> attributesArgumentCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(builder).setAllAttributes(attributesArgumentCaptor.capture());

    attributesConsumer.accept(assertThat(attributesArgumentCaptor.getValue()));
  }

  private static Stream<Arguments> processUrlArguments() {
    return Stream.of(
        // instrumentation code
        Arguments.of(
            archiveUrl(JarAnalyzer.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(PACKAGE_TYPE, "jar")
                        .hasEntrySatisfying(
                            PACKAGE_PATH,
                            path ->
                                assertThat(
                                        path.matches(
                                            "opentelemetry-javaagent-runtime-telemetry-java8-[0-9a-zA-Z-\\.]+\\.jar"))
                                    .isTrue())
                        .containsEntry(PACKAGE_DESCRIPTION, "javaagent by OpenTelemetry")
                        .containsEntry(PACKAGE_CHECKSUM_ALGORITHM, "SHA1")
                        .hasEntrySatisfying(
                            PACKAGE_CHECKSUM, checksum -> assertThat(checksum).isNotEmpty()))),
        // dummy war
        Arguments.of(
            archiveUrl(new File(System.getenv("DUMMY_APP_WAR"))),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(PACKAGE_TYPE, "war")
                        .containsEntry(PACKAGE_PATH, "app.war")
                        .containsEntry(PACKAGE_DESCRIPTION, "Dummy App by OpenTelemetry")
                        .containsEntry(PACKAGE_CHECKSUM_ALGORITHM, "SHA1")
                        .hasEntrySatisfying(
                            PACKAGE_CHECKSUM, checksum -> assertThat(checksum).isNotEmpty()))),
        // io.opentelemetry:opentelemetry-api
        Arguments.of(
            archiveUrl(Tracer.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(PACKAGE_TYPE, "jar")
                        .hasEntrySatisfying(
                            PACKAGE_PATH,
                            path ->
                                assertThat(path.matches("opentelemetry-api-[0-9a-zA-Z-\\.]+\\.jar"))
                                    .isTrue())
                        .containsEntry(PACKAGE_DESCRIPTION, "all")
                        .containsEntry(PACKAGE_CHECKSUM_ALGORITHM, "SHA1")
                        .hasEntrySatisfying(
                            PACKAGE_CHECKSUM, checksum -> assertThat(checksum).isNotEmpty()))),
        // org.springframework:spring-webmvc
        Arguments.of(
            archiveUrl(HttpRequest.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(PACKAGE_TYPE, "jar")
                        // TODO(jack-berg): can we extract version out of path to populate
                        // package.version field?
                        .hasEntrySatisfying(
                            PACKAGE_PATH,
                            path ->
                                assertThat(path.matches("spring-web-[0-9a-zA-Z-\\.]+\\.jar"))
                                    .isTrue())
                        .containsEntry(PACKAGE_DESCRIPTION, "org.springframework.web")
                        .containsEntry(PACKAGE_CHECKSUM_ALGORITHM, "SHA1")
                        .hasEntrySatisfying(
                            PACKAGE_CHECKSUM, checksum -> assertThat(checksum).isNotEmpty()))),
        // com.google.guava:guava
        Arguments.of(
            archiveUrl(ImmutableMap.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(PACKAGE_TYPE, "jar")
                        .hasEntrySatisfying(
                            PACKAGE_PATH,
                            path ->
                                assertThat(path.matches("guava-[0-9a-zA-Z-\\.]+\\.jar")).isTrue())
                        .containsEntry(PACKAGE_NAME, "com.google.guava:guava")
                        .hasEntrySatisfying(
                            PACKAGE_VERSION, version -> assertThat(version).isNotEmpty())
                        .containsEntry(PACKAGE_CHECKSUM_ALGORITHM, "SHA1")
                        .hasEntrySatisfying(
                            PACKAGE_CHECKSUM, checksum -> assertThat(checksum).isNotEmpty()))));
  }

  private static URL archiveUrl(File file) {
    try {
      return new URL("file://" + file.getAbsolutePath());
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Error creating URL for file", e);
    }
  }

  private static URL archiveUrl(Class<?> clazz) {
    return clazz.getProtectionDomain().getCodeSource().getLocation();
  }

  private static Consumer<AttributesAssert> assertAttributes(
      Consumer<AttributesAssert> attributesAssert) {
    return attributesAssert;
  }
}
