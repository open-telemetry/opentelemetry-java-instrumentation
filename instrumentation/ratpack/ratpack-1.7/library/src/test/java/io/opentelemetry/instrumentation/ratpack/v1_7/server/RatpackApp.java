/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import java.net.URI;
import ratpack.guice.Guice;
import ratpack.handling.Handler;
import ratpack.http.client.HttpClient;
import ratpack.server.RatpackServer;

public class RatpackApp {

  public static void main(String... args) throws Exception {
    RatpackServer.start(
        server ->
            server
                .registry(Guice.registry(b -> b.module(OpenTelemetryModule.class)))
                .handlers(
                    chain ->
                        chain
                            .get("ignore", ctx -> ctx.render("ignored"))
                            .all(Handler.class)
                            .get("foo", ctx -> ctx.render("hi-foo"))
                            .get(
                                "bar",
                                ctx ->
                                    ctx.get(HttpClient.class)
                                        .get(ctx.get(URI.class))
                                        .then(response -> ctx.render("hi-bar")))));
  }

  private RatpackApp() {}
}
