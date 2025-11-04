/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmittedScopeParserTest {

  @Test
  void testEmittedScopeParser() {
    String yamlContent =
        """
        scopes:
          - name: io.opentelemetry.alibaba-druid-1.0
            version: 2.14.0-alpha-SNAPSHOT
            schemaUrl: http://schema.org
          - name: io.opentelemetry.another-scope
            version: 1.0.0
            schemaUrl: null
        """;

    EmittedScope emittedScope = YamlHelper.emittedScopeParser(yamlContent);

    assertThat(emittedScope.getScopes()).isNotNull();
    assertThat(emittedScope.getScopes()).hasSize(2);

    assertThat(emittedScope.getScopes().get(0).getName())
        .isEqualTo("io.opentelemetry.alibaba-druid-1.0");
    assertThat(emittedScope.getScopes().get(0).getVersion()).isEqualTo("2.14.0-alpha-SNAPSHOT");
    assertThat(emittedScope.getScopes().get(0).getSchemaUrl()).isEqualTo("http://schema.org");

    assertThat(emittedScope.getScopes().get(1).getName())
        .isEqualTo("io.opentelemetry.another-scope");
    assertThat(emittedScope.getScopes().get(1).getVersion()).isEqualTo("1.0.0");
    assertThat(emittedScope.getScopes().get(1).getSchemaUrl()).isNull();
  }

  @Test
  void testGetScopesFromFilesSingleFile(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent =
        """
        scopes:
          - name: io.opentelemetry.test-1.0
            version: 1.0.0
            schemaUrl: https://opentelemetry.io/schemas/1.0.0
          - name: io.opentelemetry.common
            version: 2.0.0
            schemaUrl: null
        """;

    Files.writeString(telemetryDir.resolve("scope-abc123.yaml"), scopeContent);

    Set<EmittedScope.Scope> scopes =
        EmittedScopeParser.getScopesFromFiles(tempDir.toString(), "test-instrumentation");

    assertThat(scopes).hasSize(2);
    assertThat(scopes)
        .extracting(EmittedScope.Scope::getName)
        .containsExactlyInAnyOrder("io.opentelemetry.test-1.0", "io.opentelemetry.common");
  }

  @Test
  void testGetScopesFromFilesMultipleFiles(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent1 =
        """
        scopes:
          - name: io.opentelemetry.test-1.0
            version: 1.0.0
            schemaUrl: https://opentelemetry.io/schemas/1.0.0
        """;

    // has overlapping and new scopes
    String scopeContent2 =
        """
        scopes:
          - name: io.opentelemetry.test-1.0
            version: 1.0.0
            schemaUrl: https://opentelemetry.io/schemas/1.0.0
          - name: io.opentelemetry.another-2.0
            version: 2.0.0
            schemaUrl: null
        """;

    Files.writeString(telemetryDir.resolve("scope-file1.yaml"), scopeContent1);
    Files.writeString(telemetryDir.resolve("scope-file2.yaml"), scopeContent2);

    Set<EmittedScope.Scope> scopes =
        EmittedScopeParser.getScopesFromFiles(tempDir.toString(), "test-instrumentation");

    // duplicates should be removed
    assertThat(scopes).hasSize(2);
    assertThat(scopes)
        .extracting(EmittedScope.Scope::getName)
        .containsExactlyInAnyOrder("io.opentelemetry.test-1.0", "io.opentelemetry.another-2.0");
  }

  @Test
  void testGetScopesFromFilesNoScopeFiles(@TempDir Path tempDir) throws IOException {
    // Create instrumentation directory but no scope files
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    // non-scope file
    Files.writeString(telemetryDir.resolve("metrics-abc123.yaml"), "some: content");

    // Parse should return empty set
    Set<EmittedScope.Scope> scopes =
        EmittedScopeParser.getScopesFromFiles(tempDir.toString(), "test-instrumentation");

    assertThat(scopes).isEmpty();
  }

  @Test
  void testGetScopeWithMatchingName(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent =
        """
        scopes:
          - name: io.opentelemetry.test-lib-1.0
            version: 1.0.0
            schemaUrl: null
        """;

    Files.writeString(telemetryDir.resolve("scope-abc123.yaml"), scopeContent);

    FileManager fileManager = new FileManager(tempDir + "/");
    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .srcPath("test-instrumentation")
            .instrumentationName("test-lib-1.0")
            .namespace("test-lib")
            .group("test-lib")
            .build();

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getName()).isEqualTo("io.opentelemetry.test-lib-1.0");
    assertThat(scopeInfo.getSchemaUrl()).isNull();
  }

  @Test
  void testGetScopeWithSchemaUrl(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent =
        """
        scopes:
          - name: io.opentelemetry.spring-web-6.0
            version: 2.14.0
            schemaUrl: https://opentelemetry.io/schemas/1.21.0
        """;

    Files.writeString(telemetryDir.resolve("scope-test.yaml"), scopeContent);

    FileManager fileManager = new FileManager(tempDir + "/");
    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .srcPath("test-instrumentation")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .build();

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getName()).isEqualTo("io.opentelemetry.spring-web-6.0");
    assertThat(scopeInfo.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.21.0");
  }

  @Test
  void testGetScopeNoTelemetryDirectory(@TempDir Path tempDir) {
    FileManager fileManager = new FileManager(tempDir.toString() + "/");
    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .srcPath("test-instrumentation")
            .instrumentationName("test-lib-1.0")
            .namespace("test-lib")
            .group("test-lib")
            .build();

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);

    assertThat(scopeInfo).isNull();
  }

  @Test
  void testGetScopeNoMatchingScope(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent =
        """
        scopes:
          - name: io.opentelemetry.other-lib
            version: 1.0.0
            schemaUrl: null
          - name: io.opentelemetry.another-lib
            version: 2.0.0
            schemaUrl: null
        """;

    Files.writeString(telemetryDir.resolve("scope-abc123.yaml"), scopeContent);

    FileManager fileManager = new FileManager(tempDir + "/");
    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .srcPath("test-instrumentation")
            .instrumentationName("test-lib-1.0")
            .namespace("test-lib")
            .group("test-lib")
            .build();

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);

    assertThat(scopeInfo).isNull();
  }

  @Test
  void testGetScopeMultipleScopesOneMatches(@TempDir Path tempDir) throws IOException {
    Path instrumentationDir = tempDir.resolve("test-instrumentation");
    Path telemetryDir = instrumentationDir.resolve(".telemetry");
    Files.createDirectories(telemetryDir);

    String scopeContent =
        """
        scopes:
          - name: io.opentelemetry.common
            version: 1.0.0
            schemaUrl: null
          - name: io.opentelemetry.hibernate-6.0
            version: 2.14.0
            schemaUrl: https://opentelemetry.io/schemas/1.21.0
          - name: io.opentelemetry.another
            version: 3.0.0
            schemaUrl: null
        """;

    Files.writeString(telemetryDir.resolve("scope-multi.yaml"), scopeContent);

    FileManager fileManager = new FileManager(tempDir + "/");
    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .srcPath("test-instrumentation")
            .instrumentationName("hibernate-6.0")
            .namespace("hibernate")
            .group("hibernate")
            .build();

    InstrumentationScopeInfo scopeInfo = EmittedScopeParser.getScope(fileManager, module);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getName()).isEqualTo("io.opentelemetry.hibernate-6.0");
    assertThat(scopeInfo.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.21.0");
  }
}
