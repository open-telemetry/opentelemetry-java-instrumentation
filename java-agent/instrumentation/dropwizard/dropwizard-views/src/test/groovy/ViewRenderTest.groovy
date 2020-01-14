import io.dropwizard.views.View
import io.dropwizard.views.freemarker.FreemarkerViewRenderer
import io.dropwizard.views.mustache.MustacheViewRenderer
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

import java.nio.charset.StandardCharsets

import static io.opentelemetry.auto.test.asserts.ListWriterAssert.assertTraces
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

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
          operationName "view.render"
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "View $template"
            "$Tags.COMPONENT" "dropwizard-view"
            "span.origin.type" renderer.class.simpleName
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
