/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.avaje.jex.v3_0;

import io.avaje.jex.Jex;
import io.avaje.jex.Jex.Server;

public class TestJexJavaApplication {

  private TestJexJavaApplication() {}

  public static Server initJex() {
    Jex app = Jex.create().contextPath("/test");
    app.get(
        "/param/{id}",
        ctx -> {
          String paramId = ctx.pathParam("id");
          ctx.write(paramId);
        });
    app.get(
        "/error",
        ctx -> {
          throw new RuntimeException("boom");
        });
    return app.port(0).start();
  }
}
