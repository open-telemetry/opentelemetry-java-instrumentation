/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.common;

import application.io.opentelemetry.common.ComponentLoader;

public class ApplicationComponentLoader155 implements ComponentLoader {

  private final io.opentelemetry.common.ComponentLoader componentLoader;

  public ApplicationComponentLoader155(io.opentelemetry.common.ComponentLoader componentLoader) {
    this.componentLoader = componentLoader;
  }

  @Override
  public <T> Iterable<T> load(Class<T> type) {
    return componentLoader.load(type);
  }
}
