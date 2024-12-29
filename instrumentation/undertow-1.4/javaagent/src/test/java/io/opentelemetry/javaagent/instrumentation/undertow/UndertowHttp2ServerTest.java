/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;

class UndertowHttp2ServerTest extends UndertowServerTest {

  @Override
  public void configureUndertow(Undertow.Builder builder) {
    builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
  }

  @Override
  public boolean useHttp2() {
    return true;
  }
}
