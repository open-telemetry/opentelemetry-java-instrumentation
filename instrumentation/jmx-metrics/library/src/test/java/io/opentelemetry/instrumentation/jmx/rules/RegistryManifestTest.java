/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.semconv.OtelAttributes;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class RegistryManifestTest {

  @Test
  void semconvVersion() throws Exception {
    ProtectionDomain protectionDomain = OtelAttributes.class.getProtectionDomain();
    CodeSource codeSource = protectionDomain.getCodeSource();
    assertThat(codeSource).isNotNull();
    Path jarPath = Paths.get(codeSource.getLocation().toURI());

    String semconvLibraryVersion;
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Manifest jarManifest = jarFile.getManifest();
      Attributes mainAttributes = jarManifest.getMainAttributes();
      assertThat(mainAttributes.getValue("Implementation-Title"))
          .isEqualTo("opentelemetry-semconv");
      semconvLibraryVersion = mainAttributes.getValue("Implementation-Version");
    }
    assertThat(semconvLibraryVersion).isNotNull();

    Path manifestPath =
        Paths.get(System.getProperty("io.opentelemetry.registry.path")).resolve("manifest.yaml");
    assertThat(manifestPath).isNotEmptyFile();

    Map<String, Object> registryManifest;
    Load yaml = new Load(LoadSettings.builder().build());
    try (InputStream is = Files.newInputStream(manifestPath)) {
      @SuppressWarnings("unchecked")
      Map<String, Object> loaded = (Map<String, Object>) yaml.loadFromInputStream(is);
      registryManifest = loaded;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> dependencies =
        (List<Map<String, Object>>) registryManifest.get("dependencies");
    assertThat(dependencies).isNotEmpty();

    Map<String, Object> otelDep =
        dependencies.stream().filter(d -> "otel".equals(d.get("name"))).findFirst().orElse(null);
    assertThat(otelDep).isNotNull();

    String registryPath = (String) otelDep.get("registry_path");
    String schemaUrl = (String) otelDep.get("schema_url");

    assertThat(registryPath)
        .isEqualTo(
            "https://github.com/open-telemetry/semantic-conventions@v%s[model]",
            semconvLibraryVersion);
    assertThat(schemaUrl).isEqualTo("https://opentelemetry.io/schemas/%s", semconvLibraryVersion);
  }
}
