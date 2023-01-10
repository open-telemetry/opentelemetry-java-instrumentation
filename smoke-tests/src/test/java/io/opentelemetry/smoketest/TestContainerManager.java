/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.testcontainers.containers.output.OutputFrame;

public interface TestContainerManager {

  void startEnvironmentOnce();

  int getBackendMappedPort();

  int getTargetMappedPort(int originalPort);

  Consumer<OutputFrame> startTarget(
      String targetImageName,
      String agentPath,
      String jvmArgsEnvVarName,
      Map<String, String> extraEnv,
      List<ResourceMapping> extraResources,
      List<Integer> extraPorts,
      TargetWaitStrategy waitStrategy,
      String[] command);

  void stopTarget();

  static boolean useWindowsContainers() {
    return !Objects.equals(System.getenv("USE_LINUX_CONTAINERS"), "1")
        && System.getProperty("os.name").toLowerCase().contains("windows");
  }
}
