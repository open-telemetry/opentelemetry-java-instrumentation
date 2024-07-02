/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

import io.opentelemetry.agents.Agent;

/**
 * This utility class provides the standard file naming conventions, primarily for files that are
 * shared between containers and the test runner. It consolidates the naming logic into one place to
 * ensure consistency, reduce duplication, and decrease errors.
 */
public interface NamingConvention {
  /**
   * Returns a path string to the location of the k6 results json file.
   *
   * @param agent The agent to get results file path for
   */
  String k6Results(Agent agent);

  /**
   * Returns a path string to the location of the jfr output file for a given agent run.
   *
   * @param agent The agent to get the jfr file path for.
   */
  String jfrFile(Agent agent);

  /**
   * Returns the path string to the file that contains the startup duration for a given agent run.
   *
   * @param agent The agent to get the startup duration for.
   */
  String startupDurationFile(Agent agent);

  /** Returns the root path that this naming convention was configured with. */
  String root();
}
