/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import java.util.HashMap;
import java.util.Map;

public class HelperResourceBuilderImpl implements HelperResourceBuilder {

  private final Map<String, String> resourcePathMappings = new HashMap<>();

  @Override
  public void register(String resourcePath) {
    resourcePathMappings.put(resourcePath, resourcePath);
  }

  @Override
  public void register(String applicationResourcePath, String agentResourcePath) {
    resourcePathMappings.put(applicationResourcePath, agentResourcePath);
  }

  /**
   * Returns the registered mappings, where the keys are the paths in the user's class loader at
   * which to inject the resource ({@code applicationResourcePath}) and the values are the paths in
   * the agent class loader from which to get the content for the resource ({@code
   * agentResourcePath}).
   */
  public Map<String, String> getResourcePathMappings() {
    return resourcePathMappings;
  }
}
