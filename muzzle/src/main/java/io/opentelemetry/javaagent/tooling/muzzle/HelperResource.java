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
   * @param allClassLoaders True if the resource should be injected to all classloaders, false
   *     otherwise.
   */
  public static HelperResource create(
      String applicationPath, String agentPath, boolean allClassLoaders) {
    return new AutoValue_HelperResource(applicationPath, agentPath, allClassLoaders);
  }

  /** The path in the user's class loader at which to inject the resource. */
  public abstract String getApplicationPath();

  /** The path in the agent class loader from which to get the content for the resource. */
  public abstract String getAgentPath();

  /** True if the resource should be injected to all classloaders, false otherwise. */
  public abstract boolean allClassLoaders();
}
