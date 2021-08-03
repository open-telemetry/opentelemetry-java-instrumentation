/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.util;

import io.opentelemetry.agents.Agent;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NamingConvention {

  private final String dir;

  public NamingConvention(){
    this(".");
  }

  public NamingConvention(String dir) {this.dir = dir;}

  public Path k6Results(Agent agent){
    return Paths.get(dir, "k6_out_" + agent.getName() + ".json");
  }

  public Path jfrFile(Agent agent) {
    return Paths.get(dir, "petclinic-" + agent.getName() + ".jfr");
  }

  public Path startupDurationFile(Agent agent) { return Paths.get(dir, "startup-time-" + agent.getName() + ".txt"); }

  public String root() {
    return dir;
  }
}
