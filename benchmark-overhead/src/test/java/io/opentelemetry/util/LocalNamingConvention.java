/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

import io.opentelemetry.agents.Agent;
import java.nio.file.Paths;

public class LocalNamingConvention implements NamingConvention {
  private final String dir;

  public LocalNamingConvention(String dir) {
    this.dir = dir;
  }

  public String k6Results(Agent agent) {
    return Paths.get(dir, "k6_out_" + agent.getName() + ".json").toString();
  }

  public String jfrFile(Agent agent) {
    return Paths.get(dir, "petclinic-" + agent.getName() + ".jfr").toString();
  }

  public String startupDurationFile(Agent agent) {
    return Paths.get(dir, "startup-time-" + agent.getName() + ".txt").toString();
  }

  public String root() {
    return dir;
  }
}
