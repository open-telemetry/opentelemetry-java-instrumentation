/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.dropwizard.views.View
import io.dropwizard.views.freemarker.FreemarkerViewRenderer
import io.dropwizard.views.mustache.MustacheViewRenderer
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.nio.charset.StandardCharsets

class ViewRenderTest extends AgentInstrumentationSpecification {

  def "render #template succeeds with span"() {
    setup:
    def outputStream = new ByteArrayOutputStream()

    when:
    runWithSpan("parent") {
      renderer.render(view, Locale.ENGLISH, outputStream)
    }

    then:
    outputStream.toString().contains("This is an example of a view")
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "Render $template"
          childOf span(0)
        }
      }
    }

    where:
    renderer                     | template
    new FreemarkerViewRenderer() | "/views/ftl/utf8.ftl"
    new MustacheViewRenderer()   | "/views/mustache/utf8.mustache"
    new FreemarkerViewRenderer() | "/views/ftl/utf8.ftl"
    new MustacheViewRenderer()   | "/views/mustache/utf8.mustache"

    view = new View(template, StandardCharsets.UTF_8) {}
  }

  def "do not create span when there's no parent"() {
    setup:
    def outputStream = new ByteArrayOutputStream()
    def view = new View("/views/ftl/utf8.ftl", StandardCharsets.UTF_8) {}

    when:
    new FreemarkerViewRenderer().render(view, Locale.ENGLISH, outputStream)

    then:
    Thread.sleep(500)
    assert traces.isEmpty()
  }
}
