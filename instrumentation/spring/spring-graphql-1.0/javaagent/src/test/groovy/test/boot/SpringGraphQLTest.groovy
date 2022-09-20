package test.boot

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.web.reactive.server.WebTestClient

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR

class SpringGraphQLTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<ConfigurableApplicationContext> {

  def setupSpec() {
    setupServer()
  }

  def cleanupSpec() {
    cleanupServer()
  }

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig)
    app.setDefaultProperties([
      "server.port": port
    ])

    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext context) {
    context.close()
  }

  def "test #methodName"(String methodName) {
    setup:
    WebTestClient client = WebTestClient.bindToServer().baseUrl(address.resolve("graphql").toString()).build()
    HttpGraphQlTester tester = HttpGraphQlTester.create(client)
    GraphQlTester.Response response = tester.document("{ $methodName }").execute()

    expect:
    response.path(methodName).hasValue()

    and:
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, "/*")
        graphQlSpan(it, 1, methodName, span(0))
      }
    }

    where:
    methodName << ["helloWorldAsValue", "helloWorldAsMono", "helloWorldAsFlux", "helloWorldAsCallable"]
  }

  static serverSpan(TraceAssert trace, int index, String operation, Throwable exception = null) {
    trace.span(index) {
      hasNoParent()
      name operation
      kind SpanKind.SERVER
      if (exception != null) {
        status ERROR
      }
    }
  }

  static graphQlSpan(TraceAssert trace, int index, String methodName, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name "HelloController." + methodName
      kind INTERNAL
      attributes {
        "$SemanticAttributes.CODE_NAMESPACE" "test.boot.HelloController"
        "$SemanticAttributes.CODE_FUNCTION" methodName
      }
    }
  }

}
