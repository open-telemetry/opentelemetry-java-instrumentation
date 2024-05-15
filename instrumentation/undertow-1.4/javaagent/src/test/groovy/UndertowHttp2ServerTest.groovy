/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.undertow.Undertow
import io.undertow.UndertowOptions

class UndertowHttp2ServerTest extends UndertowServerTest {

  void configureUndertow(Undertow.Builder builder) {
    builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true)
  }

  boolean useHttp2() {
    true
  }
}
