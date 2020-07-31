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

package io.opentelemetry.auto.gradle.instrumentation;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.concurrent.Callable;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;

/**
 * {@link Plugin} to initialize projects that implement auto instrumentation using bytecode
 * manipulation. Currently builds the special bootstrap classpath that is needed by bytecode tests.
 */
// TODO(anuraaga): Migrate more build logic into this plugin to avoid having two places for it.
public class AutoInstrumentationPlugin implements Plugin<Project> {

  /**
   * An exact copy of {@code io.opentelemetry.auto.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. We
   * can't reference it directly since this file needs to be compiled before the other packages.
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES_COPY = {
    "io.opentelemetry.auto.common.exec",
    "io.opentelemetry.auto.slf4j",
    "io.opentelemetry.auto.config",
    "io.opentelemetry.auto.bootstrap",
    "io.opentelemetry.auto.instrumentation.api",
    "io.opentelemetry.auto.shaded",
    "io.opentelemetry.auto.typedspan",
  };

  // Aditional classes we need only for tests and aren't shared with the agent business logic.
  private static final String[] TEST_BOOTSTRAP_PREFIXES;

  static {
    final String[] testBS = {
      "io.opentelemetry.OpenTelemetry", // OpenTelemetry API
      "io.opentelemetry.common", // OpenTelemetry API
      "io.opentelemetry.context", // OpenTelemetry API (context prop)
      "io.opentelemetry.correlationcontext", // OpenTelemetry API
      "io.opentelemetry.internal", // OpenTelemetry API
      "io.opentelemetry.metrics", // OpenTelemetry API
      "io.opentelemetry.trace", // OpenTelemetry API
      "io.opentelemetry.contrib.auto.annotations", // OpenTelemetry API Contrib
      "io.grpc.Context", // OpenTelemetry API dependency
      "io.grpc.Deadline", // OpenTelemetry API dependency
      "io.grpc.PersistentHashArrayMappedTrie", // OpenTelemetry API dependency
      "io.grpc.ThreadLocalContextStorage", // OpenTelemetry API dependency
      "org.slf4j",
      "ch.qos.logback",
      // Tomcat's servlet classes must be on boostrap
      // when running tomcat test
      "javax.servlet.ServletContainerInitializer",
      "javax.servlet.ServletContext"
    };
    TEST_BOOTSTRAP_PREFIXES =
        Arrays.copyOf(
            BOOTSTRAP_PACKAGE_PREFIXES_COPY,
            BOOTSTRAP_PACKAGE_PREFIXES_COPY.length + testBS.length);
    for (int i = 0; i < testBS.length; ++i) {
      TEST_BOOTSTRAP_PREFIXES[i + BOOTSTRAP_PACKAGE_PREFIXES_COPY.length] = testBS[i];
    }
    for (int i = 0; i < TEST_BOOTSTRAP_PREFIXES.length; i++) {
      TEST_BOOTSTRAP_PREFIXES[i] = TEST_BOOTSTRAP_PREFIXES[i].replace('.', '/');
    }
  }

  @Override
  public void apply(Project project) {
    project.getPlugins().apply(JavaLibraryPlugin.class);

    project
        .getTasks()
        .withType(
            Test.class,
            task -> {
              // TODO(anuraaga): This check should probably be made more precise or remove entirely.
              // Need to check the impact of the latter first.
              if (task.getName().equals("test")) {
                return;
              }

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
                    // priority,
                    // so we exclude the later ones (we need this to make sure logback is picked
                    // up).
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
              task.jvmArgs("-Xbootclasspath/a:build/libs/" + bootstrapJarName);
            });
  }

  private static boolean isBootstrapClass(String filePath) {
    for (int i = 0; i < TEST_BOOTSTRAP_PREFIXES.length; ++i) {
      if (filePath.startsWith(TEST_BOOTSTRAP_PREFIXES[i])) {
        return true;
      }
    }
    return false;
  }
}
