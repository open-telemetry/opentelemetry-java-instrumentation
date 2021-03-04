/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT

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
import java.util.concurrent.atomic.AtomicReference
import spock.lang.AutoCleanup
import spock.lang.Shared

class Aws0ClientTest extends AgentInstrumentationSpecification {

  private static final CREDENTIALS_PROVIDER_CHAIN = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new SystemPropertiesCredentialsProvider(),
    new ProfileCredentialsProvider(),
    new InstanceProfileCredentialsProvider())

  def setupSpec() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key")
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key")
  }

  def cleanupSpec() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY)
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY)
  }

  @Shared
  def responseBody = new AtomicReference<String>()
  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      all {
        response.status(200).send(responseBody.get())
      }
    }
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
    responseBody.set(body)

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
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.HTTP_URL.key}" "$server.address"
            "${SemanticAttributes.HTTP_METHOD.key}" "$method"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "aws.service" { it.contains(service) }
            "aws.endpoint" "$server.address"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            for (def addedTag : additionalAttributes) {
              "$addedTag.key" "$addedTag.value"
            }
          }
        }
      }
    }
    server.lastRequest.headers.get("X-Amzn-Trace-Id") != null
    server.lastRequest.headers.get("traceparent") == null

    where:
    service | operation           | method | path                  | handlerCount | client                                                                      | additionalAttributes              | call                                                                                                                                   | body
    "S3"    | "CreateBucket"      | "PUT"  | "/testbucket/"        | 1            | new AmazonS3Client().withEndpoint("http://localhost:$server.address.port")  | ["aws.bucket.name": "testbucket"] | { client -> client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build()); client.createBucket("testbucket") } | ""
    "S3"    | "GetObject"         | "GET"  | "/someBucket/someKey" | 1            | new AmazonS3Client().withEndpoint("http://localhost:$server.address.port")  | ["aws.bucket.name": "someBucket"] | { client -> client.getObject("someBucket", "someKey") }                                                                                | ""
    "EC2"   | "AllocateAddress"   | "POST" | "/"                   | 4            | new AmazonEC2Client().withEndpoint("http://localhost:$server.address.port") | [:]                               | { client -> client.allocateAddress() }                                                                                                 | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """
    "RDS"   | "DeleteOptionGroup" | "POST" | "/"                   | 1            | new AmazonRDSClient().withEndpoint("http://localhost:$server.address.port") | [:]                               | { client -> client.deleteOptionGroup(new DeleteOptionGroupRequest()) }                                                                 | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """
  }

  def "send #operation request to closed port"() {
    setup:
    responseBody.set(body)

    when:
    call.call(client)

    then:
    thrown AmazonClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          errored true
          errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:${UNUSABLE_PORT}"
            "${SemanticAttributes.HTTP_METHOD.key}" "$method"
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" 61
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
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
        throw new RuntimeException("bad handler")
      }
    })

    when:
    client.getObject("someBucket", "someKey")

    then:
    !Span.current().getSpanContext().isValid()
    thrown RuntimeException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "S3.GetObject"
          kind CLIENT
          errored true
          errorEvent RuntimeException, "bad handler"
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.HTTP_URL.key}" "https://s3.amazonaws.com"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.NET_PEER_NAME.key}" "s3.amazonaws.com"
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
    def server = httpServer {
      handlers {
        all {
          Thread.sleep(500)
          response.status(200).send()
        }
      }
    }
    AmazonS3Client client = new AmazonS3Client(new ClientConfiguration().withRequestTimeout(50 /* ms */))
      .withEndpoint("http://localhost:$server.address.port")

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
          errored true
          errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.HTTP_URL.key}" "$server.address"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "aws.service" "Amazon S3"
            "aws.endpoint" "http://localhost:$server.address.port"
            "aws.operation" "GetObject"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "someBucket"
          }
        }
      }
    }

    cleanup:
    server.close()
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : "HTTP request"
  }
}
