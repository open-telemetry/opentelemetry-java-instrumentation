/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.amazonaws.AmazonClientException
import com.amazonaws.ClientConfiguration
import com.amazonaws.Request
import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.rds.AmazonRDSClient
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class Aws0ClientTest extends AgentInstrumentationSpecification {

  private static final CREDENTIALS_PROVIDER_CHAIN = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new SystemPropertiesCredentialsProvider(),
    new ProfileCredentialsProvider(),
    new InstanceProfileCredentialsProvider())

  @Shared
  def server = new MockWebServerExtension()

  def setupSpec() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key")
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key")
    server.start()
  }

  def cleanupSpec() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY)
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY)
    server.stop()
  }

  def setup() {
    server.beforeTestExecution(null)
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

  def "send #operation request with mocked response"() {
    setup:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))

    when:
    def response = call.call(client)

    then:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.size() == handlerCount
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "${server.httpUri()}"
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "aws.service" { it.contains(service) }
            "aws.endpoint" "${server.httpUri()}"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            for (def addedTag : additionalAttributes) {
              "$addedTag.key" "$addedTag.value"
            }
          }
        }
      }
    }

    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null

    where:
    service | operation           | method | path                  | handlerCount | client                                                    | additionalAttributes              | call                                                                                                                    | body
    "S3"    | "CreateBucket"      | "PUT"  | "/testbucket/"        | 1            | new AmazonS3Client().withEndpoint("${server.httpUri()}")  | ["aws.bucket.name": "testbucket"] | { c -> c.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build()); c.createBucket("testbucket") } | ""
    "S3"    | "GetObject"         | "GET"  | "/someBucket/someKey" | 1            | new AmazonS3Client().withEndpoint("${server.httpUri()}")  | ["aws.bucket.name": "someBucket"] | { c -> c.getObject("someBucket", "someKey") }                                                                           | ""
    "EC2"   | "AllocateAddress"   | "POST" | "/"                   | 4            | new AmazonEC2Client().withEndpoint("${server.httpUri()}") | [:]                               | { c -> c.allocateAddress() }                                                                                            | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """
    "RDS"   | "DeleteOptionGroup" | "POST" | "/"                   | 1            | new AmazonRDSClient().withEndpoint("${server.httpUri()}") | [:]                               | { c -> c.deleteOptionGroup(new DeleteOptionGroupRequest()) }                                                            | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """
  }

  def "send #operation request to closed port"() {
    setup:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))

    when:
    call.call(client)

    then:
    thrown AmazonClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          status ERROR
          errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "http://localhost:${UNUSABLE_PORT}"
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_PORT" 61
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "aws.service" { it.contains(service) }
            "aws.endpoint" "http://localhost:${UNUSABLE_PORT}"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            for (def addedTag : additionalAttributes) {
              "$addedTag.key" "$addedTag.value"
            }
          }
        }
      }
    }

    where:
    service | operation   | method | url                  | call                                                    | additionalAttributes              | body | client
    "S3"    | "GetObject" | "GET"  | "someBucket/someKey" | { client -> client.getObject("someBucket", "someKey") } | ["aws.bucket.name": "someBucket"] | ""   | new AmazonS3Client(CREDENTIALS_PROVIDER_CHAIN, new ClientConfiguration().withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0))).withEndpoint("http://localhost:${UNUSABLE_PORT}")
  }

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
          name "S3.GetObject"
          kind CLIENT
          status ERROR
          errorEvent IllegalStateException, "bad handler"
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "https://s3.amazonaws.com"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_NAME" "s3.amazonaws.com"
            "aws.service" "Amazon S3"
            "aws.endpoint" "https://s3.amazonaws.com"
            "aws.operation" "GetObject"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "someBucket"
          }
        }
      }
    }
  }

  // TODO(anuraaga): Add events for retries.
  def "timeout and retry errors not captured"() {
    setup:
    def response = HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(500))
    // One retry so two requests.
    server.enqueue(response)
    server.enqueue(response)
    AmazonS3Client client = new AmazonS3Client(new ClientConfiguration()
      .withRequestTimeout(50 /* ms */)
      .withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(1)))
      .withEndpoint("${server.httpUri()}")

    when:
    client.getObject("someBucket", "someKey")

    then:
    !Span.current().getSpanContext().isValid()
    thrown AmazonClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "S3.GetObject"
          kind CLIENT
          status ERROR
          errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "${server.httpUri()}"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "aws.service" "Amazon S3"
            "aws.endpoint" "${server.httpUri()}"
            "aws.operation" "GetObject"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "someBucket"
          }
        }
      }
    }
  }
}
