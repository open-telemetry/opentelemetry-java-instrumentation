/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class MuzzleTaskSelectionTest {
  private final Project rootProject = ProjectBuilder.builder().build();
  private final Project instrumentationProject =
      ProjectBuilder.builder().withName("instrumentation").withParent(rootProject).build();
  private final Project project =
      ProjectBuilder.builder().withName("example").withParent(instrumentationProject).build();

  @Test
  void preservesAggregateSelectionWithoutPartitionMetadata() {
    project.getGradle().getStartParameter().setTaskNames(List.of(":instrumentation:muzzle1"));

    assertThat(MuzzleTaskSelection.hasRelevantTask(project)).isTrue();
  }

  @Test
  void preservesAggregatePrefixMatching() {
    project
        .getExtensions()
        .getExtraProperties()
        .set(MuzzleTaskSelection.AGGREGATE_TASK_PATH_PROPERTY, ":instrumentation:muzzle2");

    for (String task : List.of("instrumentation:muzzle", ":instrumentation:muzzleAll")) {
      project.getGradle().getStartParameter().setTaskNames(List.of(task));

      assertThat(MuzzleTaskSelection.hasRelevantTask(project)).isTrue();
    }
  }

  @Test
  void ignoresUnrelatedTasks() {
    project
        .getGradle()
        .getStartParameter()
        .setTaskNames(List.of("test", ":instrumentation:other:muzzle", "check"));

    assertThat(MuzzleTaskSelection.hasRelevantTask(project)).isFalse();
  }

  @Test
  void supportsRootProjectMuzzle() {
    for (String task : List.of("muzzle", ":muzzle")) {
      rootProject.getGradle().getStartParameter().setTaskNames(List.of(task));

      assertThat(MuzzleTaskSelection.hasRelevantTask(rootProject)).isTrue();
    }
  }
}
