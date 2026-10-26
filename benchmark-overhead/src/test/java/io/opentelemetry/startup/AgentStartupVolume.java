/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import com.github.dockerjava.api.DockerClient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.ResourceReaper;

final class AgentStartupVolume {

  static String create(DockerClient docker) {
    return create(docker, "otel-startup-" + UUID.randomUUID());
  }

  static String create(DockerClient docker, String name) {
    Map<String, String> labels = new HashMap<>(DockerClientFactory.DEFAULT_LABELS);
    labels.putAll(ResourceReaper.instance().getLabels());
    docker.createVolumeCmd().withName(name).withLabels(labels).exec();
    return name;
  }

  private AgentStartupVolume() {}
}
