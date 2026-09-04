/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentracing.contrib.dropwizard.Trace;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jvmbootstraptest.AotTestApplication;
import org.junit.jupiter.api.Test;

class AotCacheTest {

  @Test
  void instrumentsApplicationLoadedFromAotCache() throws Exception {
    assumeTrue("25".equals(System.getProperty("java.specification.version")));
    assumeFalse(System.getProperty("java.vm.name").contains("OpenJ9"));

    File applicationJar =
        new File(
            IntegrationTestUtils.createJarWithClasses(
                    AotTestApplication.class.getName(), AotTestApplication.class, Trace.class)
                .toURI());
    Path tempDirectory = Files.createTempDirectory("otel-aot-test");
    Path configuration = tempDirectory.resolve("app.aotconf");
    Path cache = tempDirectory.resolve("app.aot");

    try {
      List<String> commonArguments = new ArrayList<>();
      commonArguments.add("--add-modules=java.instrument");
      commonArguments.add("-Xbootclasspath/a:" + IntegrationTestUtils.getAgentJarPath());
      commonArguments.add("-cp");
      commonArguments.add(applicationJar.getAbsolutePath());

      List<String> recordArguments = new ArrayList<>();
      recordArguments.add("-XX:AOTMode=record");
      recordArguments.add("-XX:AOTConfiguration=" + configuration);
      recordArguments.addAll(commonArguments);
      recordArguments.add(AotTestApplication.class.getName());
      assertThat(IntegrationTestUtils.runJava(recordArguments, emptyMap(), true).getExitCode())
          .isZero();

      List<String> createArguments = new ArrayList<>();
      createArguments.add("-XX:AOTMode=create");
      createArguments.add("-XX:AOTConfiguration=" + configuration);
      createArguments.add("-XX:AOTCache=" + cache);
      createArguments.add("-XX:+DisableAttachMechanism");
      createArguments.addAll(commonArguments);
      assertThat(IntegrationTestUtils.runJava(createArguments, emptyMap(), true).getExitCode())
          .isZero();

      List<String> runArguments = new ArrayList<>();
      runArguments.add("-XX:+UnlockDiagnosticVMOptions");
      runArguments.add("-XX:+VerifySharedSpaces");
      runArguments.add("-XX:AOTMode=on");
      runArguments.add("-XX:AOTCache=" + cache);
      runArguments.add("-javaagent:" + IntegrationTestUtils.getAgentJarPath());
      runArguments.add("-Dotel.javaagent.experimental.field-injection.enabled=false");
      runArguments.add("-Dotel.instrumentation.common.default-enabled=false");
      runArguments.add("-Dotel.instrumentation.external-annotations.enabled=true");
      runArguments.add("-Dotel.traces.exporter=logging");
      runArguments.add("-Dotel.metrics.exporter=none");
      runArguments.add("-Dotel.logs.exporter=none");
      runArguments.add("-Dotel.javaagent.debug=true");
      runArguments.add("-Xlog:aot=debug,aot+load=info,class+load=info,redefine+class*=debug");
      runArguments.addAll(commonArguments);
      runArguments.add(AotTestApplication.class.getName());

      IntegrationTestUtils.ProcessResult result =
          IntegrationTestUtils.runJava(runArguments, emptyMap(), true);

      assertThat(result.getExitCode()).isZero();
      assertThat(result.getOutput())
          .contains("Opened AOT cache " + cache)
          .contains("Using AOT-linked classes: true")
          .contains(AotTestApplication.class.getName() + " source: shared objects file")
          .contains("Transformed " + AotTestApplication.class.getName())
          .contains("'AotTestApplication.traced'")
          .contains("AOT_INSTRUMENTATION_MARKER");
    } finally {
      Files.deleteIfExists(cache);
      Files.deleteIfExists(configuration);
      Files.deleteIfExists(tempDirectory);
    }
  }
}
