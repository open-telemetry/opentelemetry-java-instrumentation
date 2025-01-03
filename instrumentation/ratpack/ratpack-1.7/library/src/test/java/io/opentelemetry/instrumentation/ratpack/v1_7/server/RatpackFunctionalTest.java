/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.net.URI;
import ratpack.guice.BindingsImposition;
import ratpack.impose.ForceDevelopmentImposition;
import ratpack.impose.ImpositionsSpec;
import ratpack.impose.UserRegistryImposition;
import ratpack.registry.Registry;
import ratpack.test.MainClassApplicationUnderTest;
import ratpack.test.embed.EmbeddedApp;

public class RatpackFunctionalTest extends MainClassApplicationUnderTest {

  private Registry registry;
  protected InMemorySpanExporter spanExporter;
  private final EmbeddedApp app;

  public RatpackFunctionalTest(Class<?> mainClass) throws Exception {
    super(mainClass);
    this.app =
        EmbeddedApp.of(
            server -> server.handlers(chain -> chain.get("other", ctx -> ctx.render("hi-other"))));
    getAddress();
  }

  @Override
  protected void addImpositions(ImpositionsSpec impositions) {
    impositions.add(ForceDevelopmentImposition.of(false));
    impositions.add(
        UserRegistryImposition.of(
            r -> {
              registry = r;
              spanExporter = (InMemorySpanExporter) registry.get(SpanExporter.class);
              return registry;
            }));
    impositions.add(
        BindingsImposition.of(
            bindings -> bindings.bindInstance(URI.class, app.getAddress().resolve("other"))));
  }
}
