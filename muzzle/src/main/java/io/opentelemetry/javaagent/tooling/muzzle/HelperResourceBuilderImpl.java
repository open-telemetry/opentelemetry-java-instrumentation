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
  public void register(String resourcePath) {
    resources.add(HelperResource.create(resourcePath, resourcePath));
  }

  @Override
  public void register(String applicationResourcePath, String agentResourcePath) {
    resources.add(HelperResource.create(applicationResourcePath, agentResourcePath));
  }

  public List<HelperResource> getResources() {
    return resources;
  }
}
