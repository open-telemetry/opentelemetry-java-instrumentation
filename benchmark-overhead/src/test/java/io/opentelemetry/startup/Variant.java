/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

enum Variant {
  NORMAL_NO_AGENT(false, false, "Normal, no agent", "normal-no-agent"),
  AOT_NO_AGENT(true, false, "AOT, no agent", "aot-no-agent"),
  NORMAL_AGENT(false, true, "Normal, agent", "normal-agent"),
  AOT_AGENT(true, true, "AOT, agent", "aot-agent");

  private final boolean aot;
  private final boolean agent;
  private final String label;
  private final String id;

  Variant(boolean aot, boolean agent, String label, String id) {
    this.aot = aot;
    this.agent = agent;
    this.label = label;
    this.id = id;
  }

  public boolean agent() {
    return agent;
  }

  public boolean aot() {
    return aot;
  }

  public String label() {
    return label;
  }

  public String id() {
    return id;
  }
}
