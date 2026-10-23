/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;

class TomcatSpringWarNoAgentSmokeTest extends TomcatSpringWarTest {
  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    super.configure(options);
    options.jvmArgsEnvVarName("SPRING_WAR_NO_AGENT_JVM_ARGS");
  }

  @Override
  protected boolean agentEnabled() {
    return false;
  }
}
