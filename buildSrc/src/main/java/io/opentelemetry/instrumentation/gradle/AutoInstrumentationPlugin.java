/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.gradle;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * {@link Plugin} to initialize projects that implement auto instrumentation using bytecode
 * manipulation. Currently builds the special bootstrap classpath that is needed by bytecode tests.
 */
// TODO(anuraaga): Migrate more build logic into this plugin to avoid having two places for it.
public class AutoInstrumentationPlugin implements Plugin<Project> {

  /**
   * An exact copy of {@code
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. We can't reference it
   * directly since this file needs to be compiled before the other packages.
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES_COPY = {
    "io.opentelemetry.javaagent.common.exec",
    "io.opentelemetry.javaagent.slf4j",
    "io.opentelemetry.javaagent.bootstrap",
    "io.opentelemetry.javaagent.shaded",
    "io.opentelemetry.instrumentation.auto.api",
  };

  // Aditional classes we need only for tests and aren't shared with the agent business logic.
  private static final List<String> TEST_BOOTSTRAP_PREFIXES =
      Stream.concat(
              Arrays.stream(BOOTSTRAP_PACKAGE_PREFIXES_COPY),
              Stream.of(
                  "io.opentelemetry.instrumentation.api",
                  "io.opentelemetry.common", // OpenTelemetry API
                  "io.opentelemetry.baggage", // OpenTelemetry API
                  "io.opentelemetry.context", // OpenTelemetry API (context prop)
                  "io.opentelemetry.internal", // OpenTelemetry API
                  "io.opentelemetry.metrics", // OpenTelemetry API
                  "io.opentelemetry.trace", // OpenTelemetry API
                  "io.opentelemetry.OpenTelemetry$", // OpenTelemetry API
                  "io.grpc.Context$", // OpenTelemetry API dependency
                  "io.grpc.PersistentHashArrayMappedTrie$", // OpenTelemetry API dependency
                  "org.slf4j",
                  "ch.qos.logback"))
          .map(pkg -> pkg.replace('.', '/'))
          .collect(Collectors.toUnmodifiableList());

  private static final List<String> TEST_BOOTSTRAP_CLASSES =
      Stream.of(
              "io.opentelemetry.OpenTelemetry", // OpenTelemetry API
              "io.grpc.Context", // OpenTelemetry API dependency
              "io.grpc.Deadline", // OpenTelemetry API dependency
              "io.grpc.PersistentHashArrayMappedTrie", // OpenTelemetry API dependency
              "io.grpc.ThreadLocalContextStorage", // OpenTelemetry API dependency
              // Tomcat's servlet classes must be on boostrap
              // when running tomcat test
              "javax.servlet.ServletContainerInitializer",
              "javax.servlet.ServletContext")
          .map(clz -> clz.replace('.', '/') + ".class")
          .collect(Collectors.toUnmodifiableList());

  @Override
  public void apply(Project project) {
    project.getPlugins().apply(JavaLibraryPlugin.class);

    project
        .getTasks()
        .withType(
            Test.class,
            task -> {
              TaskProvider<Jar> bootstrapJar =
                  project.getTasks().register(task.getName() + "BootstrapJar", Jar.class);

              Configuration testClasspath =
                  project.getConfigurations().findByName(task.getName() + "RuntimeClasspath");
              if (testClasspath == null) {
                // Same classpath as default test task
                testClasspath =
                    project
                        .getConfigurations()
                        .findByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
              }

              String bootstrapJarName = task.getName() + "-bootstrap.jar";

              Configuration testClasspath0 = testClasspath;
              bootstrapJar.configure(
                  jar -> {
                    jar.dependsOn(testClasspath0.getBuildDependencies());
                    jar.getArchiveFileName().set(bootstrapJarName);
                    jar.setIncludeEmptyDirs(false);
                    // Classpath is ordered in priority, but later writes into the JAR would take
                    // priority, so we exclude the later ones (we need this to make sure logback is
                    // picked up).
                    jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
                    jar.from(
                        project.files(
                            // Needs to be a Callable so it's executed lazily at runtime, instead of
                            // configuration time where the classpath may still be getting built up.
                            (Callable<?>)
                                () ->
                                    testClasspath0.resolve().stream()
                                        .filter(
                                            file ->
                                                !file.isDirectory()
                                                    && file.getName().endsWith(".jar"))
                                        .map(project::zipTree)
                                        .collect(toList())));

                    jar.eachFile(
                        file -> {
                          if (!isBootstrapClass(file.getPath())) {
                            file.exclude();
                          }
                        });
                  });

              task.dependsOn(bootstrapJar);
              task.getJvmArgumentProviders()
                  .add(
                      new InstrumentationTestArgs(
                          new File(project.getBuildDir(), "libs/" + bootstrapJarName)));
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

  private static boolean isBootstrapClass(String filePath) {
    for (String testBootstrapPrefix : TEST_BOOTSTRAP_PREFIXES) {
      if (filePath.startsWith(testBootstrapPrefix)) {
        return true;
      }
    }
    for (String testBootstrapName : TEST_BOOTSTRAP_CLASSES) {
      if (filePath.equals(testBootstrapName)) {
        return true;
      }
    }
    return false;
  }
}
