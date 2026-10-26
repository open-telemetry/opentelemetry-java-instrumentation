/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import org.testcontainers.DockerClientFactory;

public final class AgentStartupVolumeReaperProcess {

  public static void main(String[] args) {
    AgentStartupVolume.create(DockerClientFactory.instance().client(), args[0]);
    Runtime.getRuntime().halt(0);
  }

  private AgentStartupVolumeReaperProcess() {}
}
