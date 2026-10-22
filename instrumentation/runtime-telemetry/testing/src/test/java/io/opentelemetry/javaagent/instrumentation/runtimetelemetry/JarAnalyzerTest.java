/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.assertj.AttributesAssert;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpRequest;

class JarAnalyzerTest {

  @Test
  void processUrl_EmitsMavenMetadata(@TempDir Path tempDir)
      throws IOException, NoSuchAlgorithmException {
    Path jarPath = tempDir.resolve("archive.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      JarEntry pom = new JarEntry("META-INF/maven/example/archive-fixture/pom.properties");
      pom.setTime(315532800000L);
      jar.putNextEntry(pom);
      jar.write("groupId=example\nartifactId=archive-fixture\nversion=1.2.3\n".getBytes(UTF_8));
      jar.closeEntry();
    }
    String checksum =
        JarDetails.toHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jarPath)));

    processUrl_EmitsEvents(
        jarPath.toUri().toURL(),
        attributes ->
            attributes
                .containsEntry(stringKey("package.type"), "jar")
                .containsEntry(stringKey("package.path"), "archive.jar")
                .containsEntry(stringKey("package.name"), "example:archive-fixture")
                .containsEntry(stringKey("package.version"), "1.2.3")
                .containsEntry(stringKey("package.checksum_algorithm"), "SHA-256")
                .containsEntry(stringKey("package.checksum"), checksum));
  }

  @ParameterizedTest
  @MethodSource("processUrlArguments")
  void processUrl_EmitsEvents(URL archiveUrl, Consumer<AttributesAssert> attributesConsumer) {
    Logger logger = mock(Logger.class);
    LogRecordBuilder builder = mock(LogRecordBuilder.class);
    when(logger.logRecordBuilder()).thenReturn(builder);
    when(builder.setEventName(eq("package.info"))).thenReturn(builder);
    when(builder.setAllAttributes((Attributes) any())).thenReturn(builder);

    JarAnalyzer.processUrl(logger, archiveUrl);

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
                        .containsEntry(stringKey("package.type"), "jar")
                        .hasEntrySatisfying(
                            stringKey("package.path"),
                            path ->
                                assertThat(path)
                                    .matches(
                                        "opentelemetry-javaagent-runtime-telemetry-[0-9a-zA-Z-\\.]+\\.jar"))
                        .containsEntry(
                            stringKey("package.description"), "javaagent by OpenTelemetry")
                        .containsEntry(stringKey("package.checksum_algorithm"), "SHA-256")
                        .hasEntrySatisfying(
                            stringKey("package.checksum"),
                            checksum -> assertThat(checksum).matches("[0-9a-f]{64}")))),
        // dummy war
        Arguments.of(
            archiveUrl(new File(System.getenv("DUMMY_APP_WAR"))),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(stringKey("package.type"), "war")
                        .containsEntry(stringKey("package.path"), "app.war")
                        .containsEntry(
                            stringKey("package.description"), "Dummy App by OpenTelemetry")
                        .containsEntry(stringKey("package.checksum_algorithm"), "SHA-256")
                        .hasEntrySatisfying(
                            stringKey("package.checksum"),
                            checksum -> assertThat(checksum).matches("[0-9a-f]{64}")))),
        // io.opentelemetry:opentelemetry-api
        Arguments.of(
            archiveUrl(Tracer.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(stringKey("package.type"), "jar")
                        .hasEntrySatisfying(
                            stringKey("package.path"),
                            path ->
                                assertThat(path)
                                    .matches("opentelemetry-api-[0-9a-zA-Z-\\.]+\\.jar"))
                        .containsEntry(stringKey("package.description"), "all")
                        .containsEntry(stringKey("package.checksum_algorithm"), "SHA-256")
                        .hasEntrySatisfying(
                            stringKey("package.checksum"),
                            checksum -> assertThat(checksum).matches("[0-9a-f]{64}")))),
        // org.springframework:spring-webmvc
        Arguments.of(
            archiveUrl(HttpRequest.class),
            assertAttributes(
                attributes ->
                    attributes
                        .containsEntry(stringKey("package.type"), "jar")
                        // TODO(jack-berg): can we extract version out of path to populate
                        // package.version field?
                        .hasEntrySatisfying(
                            stringKey("package.path"),
                            path -> assertThat(path).matches("spring-web-[0-9a-zA-Z-\\.]+\\.jar"))
                        .containsEntry(stringKey("package.description"), "org.springframework.web")
                        .containsEntry(stringKey("package.checksum_algorithm"), "SHA-256")
                        .hasEntrySatisfying(
                            stringKey("package.checksum"),
                            checksum -> assertThat(checksum).matches("[0-9a-f]{64}")))));
  }

  private static URL archiveUrl(File file) {
    try {
      return file.toURI().toURL();
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
