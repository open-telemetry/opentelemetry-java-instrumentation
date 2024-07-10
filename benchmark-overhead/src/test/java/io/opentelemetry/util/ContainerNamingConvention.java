/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

import io.opentelemetry.agents.Agent;

public class ContainerNamingConvention implements NamingConvention {
  private final String dir;

  public ContainerNamingConvention(String dir) {
    this.dir = dir;
  }

  public String k6Results(Agent agent) {

    return String.join("/", dir, "k6_out_" + agent.getName() + ".json");
  }

  public String jfrFile(Agent agent) {
    return String.join("/", dir, "petclinic-" + agent.getName() + ".jfr");
  }

  public String startupDurationFile(Agent agent) {
    return String.join("/", dir, "startup-time-" + agent.getName() + ".txt");
  }

  public String root() {
    return dir;
  }
}
