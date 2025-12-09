/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;

@AutoService(ComponentProvider.class)
public class DistroComponentProvider implements ComponentProvider {

  @Override
  public Class<Resource> getType() {
    return Resource.class;
  }

  @Override
  public String getName() {
    return "opentelemetry_javaagent_distribution";
  }

  @Override
  public Resource create(DeclarativeConfigProperties config) {
    return DistroResourceProvider.get("opentelemetry-javaagent");
  }
}
