/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import jvmbootstraptest.AgentLoadedChecker;
import jvmbootstraptest.MyClassLoaderIsNotBootstrap;
import org.junit.jupiter.api.Test;

class AgentLoadedIntoBootstrapTest {

  @Test
  void agentLoadsInWhenSeparateJvmIsLaunched() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            AgentLoadedChecker.class.getName(),
            new String[0],
            new String[0],
            Collections.emptyMap(),
            true);

    assertThat(exitCode).isZero();
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
              mainClassName, new String[0], new String[0], Collections.emptyMap(), pathToJar, true);

      assertThat(exitCode).isZero();
    } finally {
      boolean deleted = new File(pathToJar).delete();
      if (!deleted) {
        System.err.println("Failed to delete temporary jar file: " + pathToJar);
      }
    }
  }
}
