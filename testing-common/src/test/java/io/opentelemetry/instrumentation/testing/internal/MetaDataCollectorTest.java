/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetaDataCollectorTest {

  private static final String SERVER_SERVICE_MAPPING_PROPERTY =
      "otel.instrumentation.common.server-service-mapping";
  private static final String TEST_SERVER_SERVICE_MAPPING =
      "127.0.0.1=test-server-service,localhost=test-server-service,192.0.2.1=test-server-service";

  @Test
  void excludesOnlyFixtureMappedServerServiceAttributes(@TempDir Path tempDir) throws IOException {
    String previousMapping = System.getProperty(SERVER_SERVICE_MAPPING_PROPERTY);
    String previousMetadataConfig = System.getProperty("metadataConfig");
    System.setProperty(SERVER_SERVICE_MAPPING_PROPERTY, TEST_SERVER_SERVICE_MAPPING);
    System.clearProperty("metadataConfig");
    try {
      Map<InternalAttributeKeyImpl<?>, AttributeType> attributes = new HashMap<>();
      attributes.put(key("server.service.name"), AttributeType.STRING);
      attributes.put(key("server.service.namespace"), AttributeType.STRING);
      attributes.put(key("server.address"), AttributeType.STRING);
      writeSpans(tempDir, attributes);
      String spansMetadata = readSpansMetadata(tempDir);
      assertThat(spansMetadata)
          .contains("when: default")
          .contains("name: server.address")
          .doesNotContain("server.service.name", "server.service.namespace");
    } finally {
      restoreSystemProperty(SERVER_SERVICE_MAPPING_PROPERTY, previousMapping);
      restoreSystemProperty("metadataConfig", previousMetadataConfig);
    }
  }

  @Test
  void includesServerServiceAttributesWhenMappingIsLabeled(@TempDir Path tempDir)
      throws IOException {
    String previousMapping = System.getProperty(SERVER_SERVICE_MAPPING_PROPERTY);
    String previousMetadataConfig = System.getProperty("metadataConfig");
    System.setProperty(SERVER_SERVICE_MAPPING_PROPERTY, TEST_SERVER_SERVICE_MAPPING);
    System.setProperty(
        "metadataConfig", SERVER_SERVICE_MAPPING_PROPERTY + "=" + TEST_SERVER_SERVICE_MAPPING);
    try {
      writeSpans(tempDir, singletonMap(key("server.service.name"), AttributeType.STRING));
      String spansMetadata = readSpansMetadata(tempDir);
      assertThat(spansMetadata).contains("name: server.service.name");
    } finally {
      restoreSystemProperty(SERVER_SERVICE_MAPPING_PROPERTY, previousMapping);
      restoreSystemProperty("metadataConfig", previousMetadataConfig);
    }
  }

  private static void writeSpans(
      Path tempDir, Map<InternalAttributeKeyImpl<?>, AttributeType> attributes) throws IOException {
    Map<InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
        spans =
            singletonMap(
                InstrumentationScopeInfo.create("test-instrumentation"),
                singletonMap(SpanKind.CLIENT, attributes));

    MetaDataCollector.writeTelemetryToFiles(
        tempDir.resolve("instrumentation/example/javaagent").toString(),
        emptyMap(),
        spans,
        emptyMap(),
        emptySet());
  }

  private static InternalAttributeKeyImpl<?> key(String name) {
    return (InternalAttributeKeyImpl<?>) stringKey(name);
  }

  private static void restoreSystemProperty(String property, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(property);
    } else {
      System.setProperty(property, previousValue);
    }
  }

  private static String readSpansMetadata(Path tempDir) throws IOException {
    Path telemetryDir = tempDir.resolve("instrumentation/example/.telemetry");
    try (Stream<Path> paths = Files.list(telemetryDir)) {
      Path spansFile =
          paths
              .filter(path -> path.getFileName().toString().startsWith("spans-"))
              .findFirst()
              .get();
      return new String(Files.readAllBytes(spansFile), UTF_8);
    }
  }
}
