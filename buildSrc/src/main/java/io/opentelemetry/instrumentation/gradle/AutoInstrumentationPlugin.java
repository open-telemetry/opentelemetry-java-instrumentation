/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle;

import java.io.File;
import java.util.Arrays;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * {@link Plugin} to initialize projects that implement auto instrumentation using bytecode
 * manipulation. Currently builds the special bootstrap classpath that is needed by bytecode tests.
 */
// TODO(anuraaga): Migrate more build logic into this plugin to avoid having two places for it.
public class AutoInstrumentationPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.getPlugins().apply(JavaLibraryPlugin.class);
    project
        .getTasks()
        .withType(
            Test.class,
            task -> {
              task.dependsOn(":testing-bootstrap:shadowJar");
              File testingBootstrapJar =
                  new File(
                      project.project(":testing-bootstrap").getBuildDir(),
                      "libs/testing-bootstrap.jar");
              // Make sure tests get rerun if the contents of the testing-bootstrap.jar change
              task.getInputs().property("testing-bootstrap-jar", testingBootstrapJar);
              task.getJvmArgumentProviders().add(new InstrumentationTestArgs(testingBootstrapJar));
            });
  }

  private static class InstrumentationTestArgs implements CommandLineArgumentProvider {
    private final File bootstrapJar;

    @Internal
    public File getBootstrapJar() {
      return bootstrapJar;
    }

    public InstrumentationTestArgs(File bootstrapJar) {
      this.bootstrapJar = bootstrapJar;
    }

    @Override
    public Iterable<String> asArguments() {
      return Arrays.asList(
          "-Xbootclasspath/a:" + bootstrapJar.getAbsolutePath(), "-Dnet.bytebuddy.raw=true");
    }
  }
}
