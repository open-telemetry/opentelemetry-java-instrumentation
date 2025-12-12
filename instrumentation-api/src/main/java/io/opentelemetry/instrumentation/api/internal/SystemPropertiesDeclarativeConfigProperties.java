/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import javax.annotation.Nullable;

class SystemPropertiesDeclarativeConfigProperties
    extends AbstractSystemPropertiesDeclarativeConfigProperties {

  SystemPropertiesDeclarativeConfigProperties(
      String node, @Nullable SystemPropertiesDeclarativeConfigProperties parent) {
    super(node, parent);
  }

  @Nullable
  @Override
  public String getString(String name) {
    return ConfigPropertiesUtil.getString(getSystemProperty(name));
  }

  @Override
  protected AbstractSystemPropertiesDeclarativeConfigProperties newChild(String node) {
    return new SystemPropertiesDeclarativeConfigProperties(node, this);
  }
}
