/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import java.util.ArrayList;
import java.util.List;

public class HelperResourceBuilderImpl implements HelperResourceBuilder {

  private final List<HelperResource> resources = new ArrayList<>();

  @Override
  public void register(String applicationResourcePath, String agentResourcePath) {
    resources.add(
        HelperResource.create(
            applicationResourcePath, agentResourcePath, /* allClassLoaders = */ false));
  }

  @Override
  public void registerForAllClassLoaders(String applicationResourcePath, String agentResourcePath) {
    resources.add(
        HelperResource.create(
            applicationResourcePath, agentResourcePath, /* allClassLoaders = */ true));
  }

  public List<HelperResource> getResources() {
    return resources;
  }
}
