/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import jvmbootstraptest.AgentLoadedChecker;
import jvmbootstraptest.MyClassLoaderIsNotBootstrap;
import org.junit.jupiter.api.Test;

class AgentLoadedIntoBootstrapTest {

  @Test
  void agentLoadsWhenSeparateJvmIsLaunched() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            AgentLoadedChecker.class.getName(), new String[0], new String[0], emptyMap(), true);

    assertThat(exitCode).isZero();
  }

  @Test
  void agentLoadsWhenAgentJarIsAlreadyOnBootstrapClasspath() throws Exception {
    String agentJarPath = IntegrationTestUtils.getAgentJarPath();

    IntegrationTestUtils.ProcessResult result =
        IntegrationTestUtils.runOnSeparateJvmAndCaptureOutput(
            AgentLoadedChecker.class.getName(),
            new String[] {"-Xbootclasspath/a:" + agentJarPath, "-Djdk.instrument.traceUsage=true"},
            new String[0],
            emptyMap(),
            System.getProperty("java.class.path"),
            true);

    assertThat(result.getExitCode()).isZero();
    assertThat(result.getOutput())
        .doesNotContain("Instrumentation.appendToBootstrapClassLoaderSearch has been called");
  }

  // this tests the case where someone adds the contents of opentelemetry-javaagent.jar by mistake
  // to their application's "uber.jar"
  //
  // the reason this can cause issues is because we locate the agent jar based on the CodeSource of
  // the OpenTelemetryAgent class, and then we add that jar file to the bootstrap class path
  //
  // but if we find the OpenTelemetryAgent class in an uber jar file, and we add that (whole) uber
  // jar file to the bootstrap class loader, that can cause some applications to break, as there's a
  // lot of application and library code that doesn't handle getClassLoader() returning null
  // (e.g. https://github.com/qos-ch/logback/pull/291)
  @Test
  void applicationUberJarShouldNotBeAddedToTheBootstrapClassLoader() throws Exception {
    String mainClassName = MyClassLoaderIsNotBootstrap.class.getName();
    String pathToJar =
        IntegrationTestUtils.createJarWithClasses(
                mainClassName, MyClassLoaderIsNotBootstrap.class, OpenTelemetryAgent.class)
            .getPath();

    try {
      int exitCode =
          IntegrationTestUtils.runOnSeparateJvm(
              mainClassName, new String[0], new String[0], emptyMap(), pathToJar, true);

      assertThat(exitCode).isZero();
    } finally {
      new File(pathToJar).delete();
    }
  }
}
