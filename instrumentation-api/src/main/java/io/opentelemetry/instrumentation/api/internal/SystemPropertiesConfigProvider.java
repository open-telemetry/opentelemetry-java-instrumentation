/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

class SystemPropertiesConfigProvider extends AbstractSystemPropertiesConfigProvider {
  @Override
  protected AbstractSystemPropertiesDeclarativeConfigProperties getProperties(String name) {
    return new SystemPropertiesDeclarativeConfigProperties(name, null);
  }
}
