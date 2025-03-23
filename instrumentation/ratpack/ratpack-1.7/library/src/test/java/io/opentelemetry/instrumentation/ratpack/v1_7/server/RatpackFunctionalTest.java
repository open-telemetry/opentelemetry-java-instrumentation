/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import java.net.URI;
import ratpack.guice.BindingsImposition;
import ratpack.impose.ForceDevelopmentImposition;
import ratpack.impose.ImpositionsSpec;
import ratpack.test.MainClassApplicationUnderTest;
import ratpack.test.embed.EmbeddedApp;

public class RatpackFunctionalTest extends MainClassApplicationUnderTest {

  private final EmbeddedApp app =
      EmbeddedApp.of(
          server -> server.handlers(chain -> chain.get("other", ctx -> ctx.render("hi-other"))));

  public RatpackFunctionalTest(Class<?> mainClass) throws Exception {
    super(mainClass);
    getAddress();
  }

  @Override
  protected void addImpositions(ImpositionsSpec impositions) {
    impositions.add(ForceDevelopmentImposition.of(false));
    impositions.add(
        BindingsImposition.of(
            bindings -> bindings.bindInstance(URI.class, app.getAddress().resolve("other"))));
  }

  public int getAppPort() {
    return app.getServer().getBindPort();
  }
}
