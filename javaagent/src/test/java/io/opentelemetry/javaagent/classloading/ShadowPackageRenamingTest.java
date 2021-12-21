/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.classloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.reflect.ClassPath;
import io.opentelemetry.javaagent.IntegrationTestUtils;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ShadowPackageRenamingTest {

  private static final List<String> AGENT_PACKAGE_PREFIXES =
      Arrays.asList(
          "io.opentelemetry.instrumentation.api",
          // jackson
          "com.fasterxml.jackson",
          // bytebuddy
          "net.bytebuddy",
          "org.yaml.snakeyaml",
          // disruptor
          "com.lmax.disruptor",
          // okHttp
          "okhttp3",
          "okio",
          "jnr",
          "org.objectweb.asm",
          "com.kenai",
          // Custom RxJava Utility
          "rx.__OpenTelemetryTracingUtil");

  @Test
  void agentDependenciesRenamed() throws Exception {
    Class<?> clazz =
        IntegrationTestUtils.getAgentClassLoader()
            .loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
    URL userSdk = OpenTelemetrySdk.class.getProtectionDomain().getCodeSource().getLocation();
    URL agentSdkDep =
        clazz
            .getClassLoader()
            .loadClass("io.opentelemetry.sdk.OpenTelemetrySdk")
            .getProtectionDomain()
            .getCodeSource()
            .getLocation();
    URL agentSource = clazz.getProtectionDomain().getCodeSource().getLocation();

    assertThat(agentSource.getFile()).endsWith(".jar");
    assertThat(agentSource.getProtocol()).isEqualTo("file");
    assertThat(agentSource).isEqualTo(agentSdkDep);
    assertThat(agentSource.getFile()).isNotEqualTo(userSdk.getFile());
  }

  @Test
  void agentClassesNotVisible() {
    assertThatThrownBy(
            () ->
                ClassLoader.getSystemClassLoader()
                    .loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void agentJarContainsNoBootstrapClasses() throws Exception {
    ClassPath agentClasspath = ClassPath.from(IntegrationTestUtils.getAgentClassLoader());

    ClassPath bootstrapClasspath = ClassPath.from(IntegrationTestUtils.getBootstrapProxy());
    Set<String> bootstrapClasses = new HashSet<>();
    List<String> bootstrapPrefixes = IntegrationTestUtils.getBootstrapPackagePrefixes();
    List<String> badBootstrapPrefixes = new ArrayList<>();
    for (ClassPath.ClassInfo info : bootstrapClasspath.getAllClasses()) {
      bootstrapClasses.add(info.getName());
      // make sure all bootstrap classes can be loaded from system
      ClassLoader.getSystemClassLoader().loadClass(info.getName());
      boolean goodPrefix =
          bootstrapPrefixes.stream().anyMatch(prefix -> info.getName().startsWith(prefix));
      if (info.getName().equals("io.opentelemetry.javaagent.OpenTelemetryAgent")) {
        // io.opentelemetry.javaagent.OpenTelemetryAgent isn't needed in the bootstrap prefixes
        // because it doesn't live in the bootstrap class loader, but it's still "good" for the
        // purpose of this test which is just checking all the classes sitting directly inside of
        // the agent jar
        goodPrefix = true;
      }
      if (!goodPrefix) {
        badBootstrapPrefixes.add(info.getName());
      }
    }

    List<ClassPath.ClassInfo> agentDuplicateClassFile = new ArrayList<>();
    List<String> badAgentPrefixes = new ArrayList<>();
    // TODO (trask) agentClasspath.getAllClasses() is empty
    //  so this part of the test doesn't verify what it thinks it is verifying
    for (ClassPath.ClassInfo classInfo : agentClasspath.getAllClasses()) {
      if (bootstrapClasses.contains(classInfo.getName())) {
        agentDuplicateClassFile.add(classInfo);
      }
      boolean goodPrefix =
          AGENT_PACKAGE_PREFIXES.stream()
              .anyMatch(prefix -> classInfo.getName().startsWith(prefix));
      if (!goodPrefix) {
        badAgentPrefixes.add(classInfo.getName());
      }
    }

    assertThat(agentDuplicateClassFile).isEmpty();
    assertThat(badBootstrapPrefixes).isEmpty();
    assertThat(badAgentPrefixes).isEmpty();
  }
}
