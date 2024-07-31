/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javalin.v5_0;

import io.javalin.Javalin;

public class TestJavalinJavaApplication {

  private TestJavalinJavaApplication() {}

  public static Javalin initJavalin(int port) {
    Javalin app = Javalin.create().start(port);
    app.get(
        "/param/{id}",
        ctx -> {
          String paramId = ctx.pathParam("id");
          ctx.result(paramId);
        });
    app.get(
        "/error",
        ctx -> {
          throw new RuntimeException("boom");
        });
    app.get("/async", ctx -> ctx.async(() -> ctx.result("ok")));
    return app;
  }
}
