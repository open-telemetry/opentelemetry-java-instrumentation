/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.Request
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractAws1ClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class Aws1ClientTest extends AbstractAws1ClientTest implements AgentTestTrait {
  @Override
  def configureClient(def client) {
    return client
  }

  // Verify agent instruments old and new construction patterns.

  def "request handler is hooked up with builder"() {
    setup:
    def builder = AmazonS3ClientBuilder.standard()
      .withRegion(Regions.US_EAST_1)
    if (addHandler) {
      builder.withRequestHandlers(new RequestHandler2() {})
    }
    AmazonWebServiceClient client = builder.build()

    expect:
    client.requestHandler2s != null
    client.requestHandler2s.size() == size
    client.requestHandler2s.get(position).getClass().getSimpleName() == "TracingRequestHandler"

    where:
    addHandler | size | position
    true       | 2    | 1
    false      | 1    | 0
  }

  def "request handler is hooked up with constructor"() {
    setup:
    String accessKey = "asdf"
    String secretKey = "qwerty"
    def credentials = new BasicAWSCredentials(accessKey, secretKey)
    def client = new AmazonS3Client(credentials)
    if (addHandler) {
      client.addRequestHandler(new RequestHandler2() {})
    }

    expect:
    client.requestHandler2s != null
    client.requestHandler2s.size() == size
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

    where:
    addHandler | size
    true       | 2
    false      | 1
  }

  // Test cases that require workarounds using bytecode instrumentation

  def "naughty request handler doesn't break the trace"() {
    setup:
    def client = new AmazonS3Client(CREDENTIALS_PROVIDER_CHAIN)
    client.addRequestHandler(new RequestHandler2() {
      void beforeRequest(Request<?> request) {
        throw new IllegalStateException("bad handler")
      }
    })

    when:
    client.getObject("someBucket", "someKey")

    then:
    !Span.current().getSpanContext().isValid()
    thrown IllegalStateException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "S3.HeadBucket"
          kind SpanKind.CLIENT
          status ERROR
          errorEvent IllegalStateException, "bad handler"
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "https://s3.amazonaws.com"
            "$SemanticAttributes.HTTP_METHOD" "HEAD"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_NAME" "s3.amazonaws.com"
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "Amazon S3"
            "$SemanticAttributes.RPC_METHOD" "HeadBucket"
            "aws.endpoint" "https://s3.amazonaws.com"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "someBucket"
          }
        }
      }
    }
  }
}
