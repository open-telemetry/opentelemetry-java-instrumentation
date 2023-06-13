/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.testing;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;

// only needed so that HttpServerAttributesExtractor can be added to the HTTP server instrumenter
enum MockNetServerAttributesGetter implements NetServerAttributesGetter<String, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(String s) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(String s) {
    return null;
  }
}
