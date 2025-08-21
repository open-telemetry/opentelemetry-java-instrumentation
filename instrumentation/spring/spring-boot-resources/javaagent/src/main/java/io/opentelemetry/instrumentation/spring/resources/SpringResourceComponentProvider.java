/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Declarative config spring resource provider.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class SpringResourceComponentProvider implements ComponentProvider<Resource> {

  @Override
  public Class<Resource> getType() {
    return Resource.class;
  }

  @Override
  public String getName() {
    return "spring";
  }

  @Override
  public Resource create(DeclarativeConfigProperties config) {
    return new SpringBootServiceVersionDetector()
        .createResource(config)
        .merge(new SpringBootServiceNameDetector().createResource(config));
  }
}
