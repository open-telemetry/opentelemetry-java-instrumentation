/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javalin.v7_0;

import io.javalin.Javalin;
import io.opentelemetry.instrumentation.javalin.AbstractJavalinTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavalinTest extends AbstractJavalinTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Javalin setupJavalin(int port) {
    return Javalin.create(
            config -> {
              config.jetty.port = port;
              config.routes.get(
                  "/param/{id}",
                  ctx -> {
                    String paramId = ctx.pathParam("id");
                    ctx.result(paramId);
                  });
              config.routes.get(
                  "/error",
                  ctx -> {
                    throw new RuntimeException("boom");
                  });
              config.routes.get("/async", ctx -> ctx.async(() -> ctx.result("ok")));
            })
        .start();
  }

  @Override
  protected String getJettyInstrumentation() {
    return "io.opentelemetry.jetty-12.0";
  }
}
