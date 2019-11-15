import datadog.trace.agent.test.AgentTestRunner
import io.dropwizard.views.View
import io.dropwizard.views.freemarker.FreemarkerViewRenderer
import io.dropwizard.views.mustache.MustacheViewRenderer

import java.nio.charset.StandardCharsets

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class ViewRenderTest extends AgentTestRunner {

  def "render #template succeeds with span"() {
    setup:
    def outputStream = new ByteArrayOutputStream()

    when:
    runUnderTrace("parent") {
      renderer.render(view, Locale.ENGLISH, outputStream)
    }

    then:
    outputStream.toString().contains("This is an example of a view")
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          resourceName "View $template"
          operationName "view.render"
          childOf span(0)
          tags {
            "component" "dropwizard-view"
            "span.origin.type" renderer.class.simpleName
            defaultTags()
          }
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
}
