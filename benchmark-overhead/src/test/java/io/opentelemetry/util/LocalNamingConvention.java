/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

import io.opentelemetry.agents.Agent;
import java.nio.file.Paths;

public class LocalNamingConvention implements NamingConvention {
  private final String dir;
  private final String workload;

  public LocalNamingConvention(String dir, String workload) {
    this.dir = dir;
    this.workload = workload;
  }

  public String k6Results(Agent agent) {
    return Paths.get(dir, "k6_out_" + agent.getName() + ".json").toString();
  }

  public String jfrFile(Agent agent) {
    return Paths.get(dir, workload + "-" + agent.getName() + ".jfr").toString();
  }

  public String startupDurationFile(Agent agent) {
    return Paths.get(dir, "startup-time-" + agent.getName() + ".txt").toString();
  }

  public String root() {
    return dir;
  }
}
