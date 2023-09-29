/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionkotlin

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextExtensionInstrumentationTest {
  private val animalKey: ContextKey<String> = ContextKey.named("animal")

  @Test
  fun `is instrumented`() {
    val context1 = Context.root().with(animalKey, "cat")
    val contextElement = context1.asContextElement()
    // check that the context element is from the opentelemetry-extension-kotlin that is shaded
    // inside the agent
    assertThat(contextElement.javaClass.name).startsWith("io.opentelemetry.javaagent.shaded")
    val context2 = contextElement.getOpenTelemetryContext()
    assertThat(context2.get(animalKey)).isEqualTo("cat")
    // instrumentation does not preserve context identity due to conversion between application and
    // agent context
    assert(context1 != context2) { "Not instrumented" }
  }
}
