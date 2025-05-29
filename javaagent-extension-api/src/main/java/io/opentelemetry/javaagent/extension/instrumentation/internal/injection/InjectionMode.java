/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal.injection;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum InjectionMode {
  CLASS_ONLY(true, false),
  RESOURCE_ONLY(false, true),
  CLASS_AND_RESOURCE(true, true);

  private final boolean injectClass;
  private final boolean injectResource;

  InjectionMode(boolean injectClass, boolean injectResource) {
    this.injectClass = injectClass;
    this.injectResource = injectResource;
  }

  public boolean shouldInjectClass() {
    return injectClass;
  }

  public boolean shouldInjectResource() {
    return injectResource;
  }
}
