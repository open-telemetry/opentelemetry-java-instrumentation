/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import com.amazonaws.AmazonClientException
import com.amazonaws.ClientConfiguration
import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.SdkClientException
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder
import com.amazonaws.services.kinesis.model.DeleteStreamRequest
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class AbstractAws1ClientTest extends InstrumentationSpecification {

  abstract <T> T configureClient(T client)

  static final CREDENTIALS_PROVIDER_CHAIN = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new SystemPropertiesCredentialsProvider(),
    new ProfileCredentialsProvider(),
    new InstanceProfileCredentialsProvider())

  @Shared
  def credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())

  @Shared
  def server = new MockWebServerExtension()

  @Shared
  def endpoint

  def setupSpec() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key")
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key")
    server.start()
    endpoint = new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-west-2")
  }

  def cleanupSpec() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY)
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY)
    server.stop()
  }

  def setup() {
    server.beforeTestExecution(null)
  }

  @Unroll
  def "send #operation request with mocked response"() {
    setup:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))

    when:
    def client = configureClient(clientBuilder).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    def response = call.call(client)

    then:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.find{it.getClass().getSimpleName() == "TracingRequestHandler"} != null

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind operation == "SendMessage" ? PRODUCER : CLIENT
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
    service      | operation           | method | path                  | clientBuilder                                                     | call                                                                            | additionalAttributes              | body
    "S3"         | "CreateBucket"      | "PUT"  | "/testbucket/"        | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true) | { c -> c.createBucket("testbucket") }                                           | ["aws.bucket.name": "testbucket"] | ""
    "S3"         | "GetObject"         | "GET"  | "/someBucket/someKey" | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true) | { c -> c.getObject("someBucket", "someKey") }                                   | ["aws.bucket.name": "someBucket"] | ""
    "DynamoDBv2" | "CreateTable"       | "POST" | "/"                   | AmazonDynamoDBClientBuilder.standard()                            | { c -> c.createTable(new CreateTableRequest("sometable", null)) }               | ["aws.table.name": "sometable"]   | ""
    "Kinesis"    | "DeleteStream"      | "POST" | "/"                   | AmazonKinesisClientBuilder.standard()                             | { c -> c.deleteStream(new DeleteStreamRequest().withStreamName("somestream")) } | ["aws.stream.name": "somestream"] | ""
    "EC2"        | "AllocateAddress"   | "POST" | "/"                   | AmazonEC2ClientBuilder.standard()                                 | { c -> c.allocateAddress() }                                                    | [:]                               | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId>
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
      """
    "RDS"        | "DeleteOptionGroup" | "POST" | "/"                   | AmazonRDSClientBuilder.standard()                                 | { c -> c.deleteOptionGroup(new DeleteOptionGroupRequest()) }                    | [:]                               | """
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
    def client = configureClient(clientBuilder)
      .withCredentials(CREDENTIALS_PROVIDER_CHAIN)
      .withClientConfiguration(new ClientConfiguration().withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0)))
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://127.0.0.1:${UNUSABLE_PORT}", "us-east-1"))
      .build()
    call.call(client)

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          status ERROR
          errorEvent SdkClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "http://127.0.0.1:${UNUSABLE_PORT}"
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT" 61
            "aws.service" { it.contains(service) }
            "aws.endpoint" "http://127.0.0.1:${UNUSABLE_PORT}"
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
    service | operation   | method | url                  | call                                          | additionalAttributes              | body | clientBuilder
    "S3"    | "GetObject" | "GET"  | "someBucket/someKey" | { c -> c.getObject("someBucket", "someKey") } | ["aws.bucket.name": "someBucket"] | ""   | AmazonS3ClientBuilder.standard()
  }

  // TODO(anuraaga): Add events for retries.
  def "timeout and retry errors not captured"() {
    setup:
    // One retry so two requests.
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(500)))
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(500)))
    AmazonS3Client client = configureClient(AmazonS3ClientBuilder.standard())
      .withClientConfiguration(new ClientConfiguration()
        .withRequestTimeout(50 /* ms */)
        .withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(1)))
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-east-1"))
      .build()

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
          try {
            errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          } catch (AssertionError e) {
            errorEvent SdkClientException, "Unable to execute HTTP request: Request did not complete before the request timeout configuration."
          }
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "${server.httpUri()}"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
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
