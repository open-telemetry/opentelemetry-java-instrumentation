/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import javax.annotation.Nullable;

class SystemPropertiesDeclarativeConfigProperties
    extends AbstractBridgedDeclarativeConfigProperties {

  SystemPropertiesDeclarativeConfigProperties(
      String node, @Nullable SystemPropertiesDeclarativeConfigProperties parent) {
    super(node, parent);
  }

  @Nullable
  @Override
  public String getStringValue(String systemPropertyKey) {
    return ConfigPropertiesUtil.getString(systemPropertyKey);
  }

  @Override
  protected AbstractBridgedDeclarativeConfigProperties newChild(String node) {
    return new SystemPropertiesDeclarativeConfigProperties(node, this);
  }
}
