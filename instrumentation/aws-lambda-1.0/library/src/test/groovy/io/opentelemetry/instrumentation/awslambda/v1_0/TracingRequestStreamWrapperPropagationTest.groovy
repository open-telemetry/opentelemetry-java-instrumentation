/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared

import java.nio.charset.Charset

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class TracingRequestStreamWrapperPropagationTest extends LibraryInstrumentationSpecification {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  static class TestRequestHandler implements RequestStreamHandler {

    @Override
    void handleRequest(InputStream input, OutputStream output, Context context) {

      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output))

      JsonNode root = OBJECT_MAPPER.readTree(input)
      String body = root.get("body").asText()
      if (body == "hello") {
        writer.write("world")
        writer.flush()
        writer.close()
      } else {
        throw new IllegalArgumentException("bad argument")
      }
    }
  }

  @Shared
  TracingRequestStreamWrapper wrapper

  def setup() {
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, "io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestStreamWrapperPropagationTest\$TestRequestHandler::handleRequest")
    wrapper = new TracingRequestStreamWrapper(testRunner().openTelemetrySdk, WrappedLambda.fromConfiguration())
  }

  def cleanup() {
    environmentVariables.clear(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY)
  }

  def "handler traced with trace propagation"() {
    when:
    String content =
      "{" +
        "\"headers\" : {" +
        "\"traceparent\": \"00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01\"" +
        "}," +
        "\"body\" : \"hello\"" +
        "}"
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    def input = new ByteArrayInputStream(content.getBytes(Charset.defaultCharset()))
    def output = new ByteArrayOutputStream()

    wrapper.handleRequest(input, output, context)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("my_function")
          kind SERVER
          attributes {
            "$SemanticAttributes.FAAS_EXECUTION" "1-22-333"
          }
        }
      }
    }
  }

  def "handler traced with exception and trace propagation"() {
    when:
    String content =
      "{" +
        "\"headers\" : {" +
        "\"traceparent\": \"00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01\"" +
        "}," +
        "\"body\" : \"bye\"" +
        "}"
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    def input = new ByteArrayInputStream(content.getBytes(Charset.defaultCharset()))
    def output = new ByteArrayOutputStream()

    def thrown
    try {
      wrapper.handleRequest(input, output, context)
    } catch (Throwable t) {
      thrown = t
    }

    then:
    thrown != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("my_function")
          kind SERVER
          status ERROR
          errorEvent(IllegalArgumentException, "bad argument")
          attributes {
            "$SemanticAttributes.FAAS_EXECUTION" "1-22-333"
          }
        }
      }
    }
  }

}
