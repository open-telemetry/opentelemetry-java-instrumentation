/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2


import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import org.junit.jupiter.api.Assumptions
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest
import software.amazon.awssdk.services.rds.RdsAsyncClient
import software.amazon.awssdk.services.rds.RdsClient
import software.amazon.awssdk.services.rds.model.DeleteOptionGroupRequest
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.Future

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

@Unroll
abstract class AbstractAws2ClientTest extends AbstractAws2ClientCoreTest {
  void assumeSupportedConfig(service, operation) {
    Assumptions.assumeFalse(
        service == "Sqs"
            && operation == "SendMessage"
            && isSqsAttributeInjectionEnabled(),
        "Cannot check Sqs.SendMessage here due to hard-coded MD5.")
  }

  // Force localhost instead of relying on mock server because using ip is yet another corner case of the virtual
  // bucket changes introduced by aws sdk v2.18.0. When using IP, there is no way to prefix the hostname with the
  // bucket name as label.
  def clientUri = URI.create("http://localhost:${server.httpPort()}")

  def "send #operation request with builder #builder.class.getName() mocked response"() {
    assumeSupportedConfig(service, operation)

    setup:
    configureSdkClient(builder)
    def client = builder
      .endpointOverride(clientUri)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null
    response.class.simpleName.startsWith(operation) || response instanceof ResponseInputStream

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind operation != "SendMessage" ? CLIENT : PRODUCER
          hasNoParent()
          attributes {
            if (service == "S3") {
              // Starting with AWS SDK V2 2.18.0, the s3 sdk will prefix the hostname with the bucket name in case
              // the bucket name is a valid DNS label, even in the case that we are using an endpoint override.
              // Previously the sdk was only doing that if endpoint had "s3" as label in the FQDN.
              // Our test assert both cases so that we don't need to know what version is being tested.
              "$SemanticAttributes.NET_PEER_NAME" { it == "somebucket.localhost" || it == "localhost" }
              "$SemanticAttributes.HTTP_URL" { it.startsWith("http://somebucket.localhost:${server.httpPort()}") || it.startsWith("http://localhost:${server.httpPort()}/somebucket") }
            } else {
              "$SemanticAttributes.NET_PEER_NAME" "localhost"
              "$SemanticAttributes.HTTP_URL" { it.startsWith("http://localhost:${server.httpPort()}") }
            }
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.USER_AGENT_ORIGINAL" { it.startsWith("aws-sdk-java/") }
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "$service"
            "$SemanticAttributes.RPC_METHOD" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" "someurl"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            }
          }
        }
      }
    }
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null

    where:
    service   | operation           | method | requestId                              | builder                 | call                                                                                             | body
    "S3"      | "CreateBucket"      | "PUT"  | "UNKNOWN"                              | S3Client.builder()      | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }              | ""
    "S3"      | "GetObject"         | "GET"  | "UNKNOWN"                              | S3Client.builder()      | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build()) }     | ""
    "Kinesis" | "DeleteStream"      | "POST" | "UNKNOWN"                              | KinesisClient.builder() | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }          | ""
    "Sqs"     | "CreateQueue"       | "POST" | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsClient.builder()     | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }              | """
        <CreateQueueResponse>
            <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
            <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
        </CreateQueueResponse>
        """
    "Sqs"     | "SendMessage"       | "POST" | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsClient.builder()     | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl("someurl").messageBody("").build()) } | """
        <SendMessageResponse>
            <SendMessageResult>
                <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
            </SendMessageResult>
            <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
        </SendMessageResponse>
        """
    "Ec2"     | "AllocateAddress"   | "POST" | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | Ec2Client.builder()     | { c -> c.allocateAddress() }                                                                     | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
        """
    "Rds"     | "DeleteOptionGroup" | "POST" | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | RdsClient.builder()     | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                         | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata><RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId></ResponseMetadata>
        </DeleteOptionGroupResponse>
        """
  }

  def "send #operation async request with builder #builder.class.getName() mocked response"() {
    assumeSupportedConfig(service, operation)
    setup:
    configureSdkClient(builder)
    def client = builder
      .endpointOverride(clientUri)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind operation != "SendMessage" ? CLIENT : PRODUCER
          hasNoParent()
          attributes {
            if (service == "S3") {
              // Starting with AWS SDK V2 2.18.0, the s3 sdk will prefix the hostname with the bucket name in case
              // the bucket name is a valid DNS label, even in the case that we are using an endpoint override.
              // Previously the sdk was only doing that if endpoint had "s3" as label in the FQDN.
              // Our test assert both cases so that we don't need to know what version is being tested.
              "$SemanticAttributes.NET_PEER_NAME" { it == "somebucket.localhost" || it == "localhost" }
              "$SemanticAttributes.HTTP_URL" { it.startsWith("http://somebucket.localhost:${server.httpPort()}") || it.startsWith("http://localhost:${server.httpPort()}") }
            } else {
              "$SemanticAttributes.NET_PEER_NAME" "localhost"
              "$SemanticAttributes.HTTP_URL" "http://localhost:${server.httpPort()}"
            }
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.USER_AGENT_ORIGINAL" { it.startsWith("aws-sdk-java/") }
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "$service"
            "$SemanticAttributes.RPC_METHOD" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" "someurl"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            }
          }
        }
      }
    }
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null

    if (service == "Sns" && operation == "Publish") {
      def content = request.request().content().toStringUtf8()
      def containsId = content.contains("${traces[0][0].traceId}-${traces[0][0].spanId}")
      def containsTp = content.contains("=traceparent")
      if (isSqsAttributeInjectionEnabled()) {
        assert containsId && containsTp
      } else {
        assert !containsId && !containsTp
      }
    }

    where:
    service | operation           | method | requestId                              | builder                  | call                                                                                                                             | body
    "S3"    | "CreateBucket"      | "PUT"  | "UNKNOWN"                              | S3AsyncClient.builder()  | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }                                              | ""
    "S3"    | "GetObject"         | "GET"  | "UNKNOWN"                              | S3AsyncClient.builder()  | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build(), AsyncResponseTransformer.toBytes()) } | "1234567890"
    // Kinesis seems to expect an http2 response which is incompatible with our test server.
    // "Kinesis"  | "DeleteStream"      | "POST" | "/"                   | "UNKNOWN"                              | KinesisAsyncClient.builder()  | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }                                          | ""
    "Sqs"   | "CreateQueue"       | "POST" | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsAsyncClient.builder() | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }                                              | """
        <CreateQueueResponse>
            <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
            <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
        </CreateQueueResponse>
        """
    "Sqs"   | "SendMessage"       | "POST" | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsAsyncClient.builder() | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl("someurl").messageBody("").build()) }                                 | """
        <SendMessageResponse>
            <SendMessageResult>
                <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
            </SendMessageResult>
            <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
        </SendMessageResponse>
        """
    "Ec2"   | "AllocateAddress"   | "POST" | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | Ec2AsyncClient.builder() | { c -> c.allocateAddress() }                                                                                                     | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
        """
    "Rds"   | "DeleteOptionGroup" | "POST" | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | RdsAsyncClient.builder() | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                                                         | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata><RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId></ResponseMetadata>
        </DeleteOptionGroupResponse>
        """
    "Sns" | "Publish" | "POST" | "f187a3c1-376f-11df-8963-01868b7c937a" | SnsAsyncClient.builder() | { SnsAsyncClient c -> c.publish(r -> r.message("hello")) } | """
      <PublishResponse xmlns="https://sns.amazonaws.com/doc/2010-03-31/">
          <PublishResult>
              <MessageId>94f20ce6-13c5-43a0-9a9e-ca52d816e90b</MessageId>
          </PublishResult>
          <ResponseMetadata>
              <RequestId>f187a3c1-376f-11df-8963-01868b7c937a</RequestId>
          </ResponseMetadata>
      </PublishResponse> 
      """
  }

  // TODO(anuraaga): Without AOP instrumentation of the HTTP client, we cannot model retries as
  // spans because of https://github.com/aws/aws-sdk-java-v2/issues/1741. We should at least tweak
  // the instrumentation to add Events for retries instead.
  def "timeout and retry errors not captured"() {
    setup:
    // One retry so two requests.
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(5000)))
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(5000)))
    def client = S3Client.builder()
      .overrideConfiguration(createOverrideConfigurationBuilder()
        .retryPolicy(RetryPolicy.builder().numRetries(1).build())
        .build())
      .endpointOverride(clientUri)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .httpClientBuilder(ApacheHttpClient.builder().socketTimeout(Duration.ofMillis(50)))
      .build()

    when:
    client.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build())

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "S3.GetObject"
          kind CLIENT
          status ERROR
          errorEvent SdkClientException, "Unable to execute HTTP request: Read timed out"
          hasNoParent()
          attributes {
            // Starting with AWS SDK V2 2.18.0, the s3 sdk will prefix the hostname with the bucket name in case
            // the bucket name is a valid DNS label, even in the case that we are using an endpoint override.
            // Previously the sdk was only doing that if endpoint had "s3" as label in the FQDN.
            // Our test assert both cases so that we don't need to know what version is being tested.
            "$SemanticAttributes.NET_PEER_NAME" { it == "somebucket.localhost" || it == "localhost" }
            "$SemanticAttributes.HTTP_URL" { it == "http://somebucket.localhost:${server.httpPort()}/somekey" || it == "http://localhost:${server.httpPort()}/somebucket/somekey" }
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "S3"
            "$SemanticAttributes.RPC_METHOD" "GetObject"
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "somebucket"
          }
        }
      }
    }
  }
}
