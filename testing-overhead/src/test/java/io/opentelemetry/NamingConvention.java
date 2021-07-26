/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import io.opentelemetry.agents.Agent;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NamingConvention {

  private final String dir;

  NamingConvention(){
    this(".");
  }

  NamingConvention(String dir) {this.dir = dir;}

  public Path k6Results(Agent agent){
    return Paths.get(dir, "k6_out_" + agent.getName() + ".json");
  }

  public Path jfrFile(Agent agent) {
    return Paths.get(dir, "petclinic-" + agent.getName() + ".jfr");
  }

  Path startupDurationFile(Agent agent) { return Paths.get(dir, "startup-time-" + agent.getName() + ".txt"); }
}
