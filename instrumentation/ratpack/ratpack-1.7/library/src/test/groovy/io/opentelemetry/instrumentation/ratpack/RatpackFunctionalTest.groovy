/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import ratpack.guice.BindingsImposition
import ratpack.impose.ForceDevelopmentImposition
import ratpack.impose.ImpositionsSpec
import ratpack.impose.UserRegistryImposition
import ratpack.registry.Registry
import ratpack.test.MainClassApplicationUnderTest
import ratpack.test.embed.EmbeddedApp

class RatpackFunctionalTest extends MainClassApplicationUnderTest {

  Registry registry
  @Lazy InMemorySpanExporter spanExporter = registry.get(SpanExporter) as InMemorySpanExporter
  EmbeddedApp app =  EmbeddedApp.of { server ->
    server.handlers { chain ->
      chain.get("other") { ctx -> ctx.render("hi-other") }
    }
  }

  RatpackFunctionalTest(Class<?> mainClass) {
    super(mainClass)
    getAddress()
  }

  @Override
  void addImpositions(ImpositionsSpec impositions) {
    impositions.add(ForceDevelopmentImposition.of(false))
    impositions.add(UserRegistryImposition.of { r ->
      registry = r
      registry
    })
    impositions.add(BindingsImposition.of {
      it.bindInstance(URI, app.address.resolve("other"))
    })
  }
}
