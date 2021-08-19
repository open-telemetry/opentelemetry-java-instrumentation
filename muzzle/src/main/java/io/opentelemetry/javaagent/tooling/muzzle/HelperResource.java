/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class HelperResource {

  /**
   * Create a new helper resource object.
   *
   * @param applicationPath The path in the user's class loader at which to inject the resource.
   * @param agentPath The path in the agent class loader from which to get the content for the
   *     resource.
   */
  public static HelperResource create(String applicationPath, String agentPath) {
    return new AutoValue_HelperResource(applicationPath, agentPath);
  }

  /** The path in the user's class loader at which to inject the resource. */
  public abstract String getApplicationPath();

  /** The path in the agent class loader from which to get the content for the resource. */
  public abstract String getAgentPath();
}
