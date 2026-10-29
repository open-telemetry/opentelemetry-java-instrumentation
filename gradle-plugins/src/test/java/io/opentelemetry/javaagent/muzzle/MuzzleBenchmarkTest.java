/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kotlin.random.Random;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleBenchmarkTest {
  @TempDir Path temp;

  @Test
  void manifestIncludesArtifactContentsAndAssertionMode() throws IOException {
    Path library = temp.resolve("library.jar");
    Files.writeString(library, "fixture");
    Project project = ProjectBuilder.builder().build();
    Configuration configuration = project.getConfigurations().create("benchmark");
    configuration.getDependencies().add(project.getDependencies().create(project.files(library)));
    Path reports = temp.resolve("reports");

    MuzzleBenchmark.record(
        reports.toFile(),
        ":example:muzzle-AssertPass-library",
        true,
        singleton("excluded-instrumentation"),
        singleton("example.ExcludedModule"),
        configuration.getIncoming().artifactView(view -> view.setLenient(true)).getArtifacts());

    try (java.util.stream.Stream<Path> files = Files.list(reports)) {
      List<Path> recorded = files.collect(toList());
      assertThat(recorded).hasSize(1);
      assertThat(Files.readAllLines(recorded.get(0)))
          .startsWith(":example:muzzle-AssertPass-library", "assertPass\ttrue")
          .contains(
              "excluded-name\texcluded-instrumentation", "excluded-module\texample.ExcludedModule")
          .anySatisfy(
              line ->
                  assertThat(line)
                      .contains("library.jar\t")
                      .endsWith(
                          "f16d05ec6b29248d2c61adb1e9263f78e4f7bace1b955014a2d17872cfe4064d"));
    }
  }

  @Test
  void offlineRangeResolutionReadsFrozenMetadata() throws Exception {
    Path metadata = temp.resolve("example/library/maven-metadata-MavenCentral.xml");
    Files.createDirectories(metadata.getParent());
    Files.writeString(
        metadata,
        "<metadata><groupId>example</groupId><artifactId>library</artifactId>"
            + "<versioning><versions><version>1.0</version><version>2.0</version>"
            + "<version>3.0</version><version>6.0-rc2</version>"
            + "</versions></versioning></metadata>");
    RepositorySystem system =
        MavenRepositorySystemUtils.newServiceLocator().getService(RepositorySystem.class);
    RepositorySystemSession session =
        MuzzleBenchmark.repositorySession(system, temp.toFile(), true);
    VersionRangeRequest request =
        new VersionRangeRequest()
            .setArtifact(new DefaultArtifact("example:library:[1.0,3.0)"))
            .setRepositories(
                singletonList(
                    new RemoteRepository.Builder(
                            "MavenCentral", "default", "https://muzzle-benchmark.invalid/")
                        .build()));

    assertThat(system.resolveVersionRange(session, request).getVersions())
        .extracting(Object::toString)
        .containsExactly("1.0", "2.0");
    request.setArtifact(new DefaultArtifact("example:library:[6.0,)"));
    org.eclipse.aether.resolution.VersionRangeResult empty =
        system.resolveVersionRange(session, request);
    assertThat(empty.getVersions()).isEmpty();
    MuzzleBenchmark.requireVersionMetadata(system, session, empty, false);
    request.setArtifact(new DefaultArtifact("example:missing:[1.0,3.0)"));
    org.eclipse.aether.resolution.VersionRangeResult missing =
        system.resolveVersionRange(session, request);
    assertThat(missing.getVersions()).isEmpty();
    assertThatThrownBy(
            () -> MuzzleBenchmark.requireVersionMetadata(system, session, missing, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing Muzzle benchmark version metadata");
    MuzzleBenchmark.requireVersionMetadata(system, session, missing, true);
    assertThat(session.isOffline()).isTrue();
  }

  @Test
  void sampleDoesNotDependOnProjectVisitation() {
    Random expected = MuzzleBenchmark.randomFor(":project:group:module", 42);
    MuzzleBenchmark.randomFor(":unrelated-project:group:module", 42).nextInt();
    Random actual = MuzzleBenchmark.randomFor(":project:group:module", 42);

    for (int i = 0; i < 40; i++) {
      assertThat(actual.nextInt()).isEqualTo(expected.nextInt());
    }
  }

  @Test
  void seedAndDirectiveIdentityControlTheSample() {
    int expected = MuzzleBenchmark.randomFor(":project:group:module", 42).nextInt();

    assertThat(MuzzleBenchmark.randomFor(":project:group:module", 43).nextInt())
        .isNotEqualTo(expected);
    assertThat(MuzzleBenchmark.randomFor(":other-project:group:module", 42).nextInt())
        .isNotEqualTo(expected);
  }
}
