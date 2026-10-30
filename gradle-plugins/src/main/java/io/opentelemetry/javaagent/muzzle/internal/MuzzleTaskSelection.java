/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import java.util.regex.Pattern;
import org.gradle.api.Project;

/** Selects projects before resolving Muzzle versions and registering assertions. */
public final class MuzzleTaskSelection {
  public static final String AGGREGATE_TASK_PATH_PROPERTY = "otel.muzzle.aggregateTaskPath";

  private static final Pattern partitionTaskPattern =
      Pattern.compile("instrumentation:muzzle[0-9]+");

  public static boolean hasRelevantTask(Project project) {
    String projectPath = project.getPath().substring(1);
    String muzzleTaskName = projectPath.isEmpty() ? "muzzle" : projectPath + ":muzzle";
    Object aggregateTaskPath = project.findProperty(AGGREGATE_TASK_PATH_PROPERTY);

    for (String requestedTask : project.getGradle().getStartParameter().getTaskNames()) {
      String taskName = requestedTask.startsWith(":") ? requestedTask.substring(1) : requestedTask;
      if (taskName.equals(muzzleTaskName)
          || taskName.startsWith("muzzle-Assert")
          || taskName.contains(":muzzle-Assert")) {
        return true;
      }
      if (taskName.startsWith("instrumentation:muzzle")
          && (aggregateTaskPath == null
              || !partitionTaskPattern.matcher(taskName).matches()
              || (":" + taskName).equals(aggregateTaskPath))) {
        return true;
      }
    }
    return false;
  }

  private MuzzleTaskSelection() {}
}
