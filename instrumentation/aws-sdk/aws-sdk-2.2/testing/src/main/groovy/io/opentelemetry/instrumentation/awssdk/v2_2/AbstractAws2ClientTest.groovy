/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2


import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpData
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.common.ResponseHeaders
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
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Future

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

@Unroll
abstract class AbstractAws2ClientTest extends AbstractAws2ClientCoreTest {
  static final String QUEUE_URL = "http://xxx/somequeue"

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

  def s3ClientBuilder() {
    def builder = S3Client.builder()
    if (Boolean.getBoolean("testLatestDeps")) {
      builder.forcePathStyle(true)
    }
    return builder
  }

  def s3AsyncClientBuilder() {
    def builder = S3AsyncClient.builder()
    if (Boolean.getBoolean("testLatestDeps")) {
      builder.forcePathStyle(true)
    }
    return builder
  }

  def "send #operation request with builder #builder.class.getName() mocked response"() {
    assumeSupportedConfig(service, operation)

    setup:
    configureSdkClient(builder)
    def client = builder
        .endpointOverride(clientUri)
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .build()

    if (body instanceof Closure) {
      server.enqueue(body.call())
    } else {
      server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))
    }

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
          name operation != "SendMessage" ? "$service.$operation" : "somequeue publish"
          kind operation != "SendMessage" ? CLIENT : PRODUCER
          hasNoParent()
          attributes {
            if (service == "S3") {
              // Starting with AWS SDK V2 2.18.0, the s3 sdk will prefix the hostname with the bucket name in case
              // the bucket name is a valid DNS label, even in the case that we are using an endpoint override.
              // Previously the sdk was only doing that if endpoint had "s3" as label in the FQDN.
              // Our test assert both cases so that we don't need to know what version is being tested.
              "$ServerAttributes.SERVER_ADDRESS" { it == "somebucket.localhost" || it == "localhost" }
              "$UrlAttributes.URL_FULL" { it.startsWith("http://somebucket.localhost:${server.httpPort()}") || it.startsWith("http://localhost:${server.httpPort()}/somebucket") }
            } else {
              "$ServerAttributes.SERVER_ADDRESS" "localhost"
              "$UrlAttributes.URL_FULL" { it.startsWith("http://localhost:${server.httpPort()}") }
            }
            "$ServerAttributes.SERVER_PORT" server.httpPort()
            "$HttpAttributes.HTTP_REQUEST_METHOD" "$method"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$RpcIncubatingAttributes.RPC_SYSTEM" "aws-api"
            "$RpcIncubatingAttributes.RPC_SERVICE" "$service"
            "$RpcIncubatingAttributes.RPC_METHOD" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" QUEUE_URL
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "somequeue"
              "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
              "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
              "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            } else if (service == "Sns") {
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "somearn"
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
    "S3"      | "CreateBucket"      | "PUT"  | "UNKNOWN"                              | s3ClientBuilder()       | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }              | ""
    "S3"      | "GetObject"         | "GET"  | "UNKNOWN"                              | s3ClientBuilder()       | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build()) }     | ""
    "Kinesis" | "DeleteStream"      | "POST" | "UNKNOWN"                              | KinesisClient.builder() | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }          | ""
    "Sns"     | "Publish"           | "POST" | "d74b8436-ae13-5ab4-a9ff-ce54dfea72a0" | SnsClient.builder()     | { c -> c.publish(PublishRequest.builder().message("somemessage").topicArn("somearn").build()) }  | """
          <PublishResponse xmlns="https://sns.amazonaws.com/doc/2010-03-31/">
              <PublishResult>
                  <MessageId>567910cd-659e-55d4-8ccb-5aaf14679dc0</MessageId>
              </PublishResult>
              <ResponseMetadata>
                  <RequestId>d74b8436-ae13-5ab4-a9ff-ce54dfea72a0</RequestId>
              </ResponseMetadata>
          </PublishResponse>
      """
    "Sns"     | "Publish"           | "POST" | "d74b8436-ae13-5ab4-a9ff-ce54dfea72a0" | SnsClient.builder()     | { c -> c.publish(PublishRequest.builder().message("somemessage").targetArn("somearn").build()) } | """
          <PublishResponse xmlns="https://sns.amazonaws.com/doc/2010-03-31/">
              <PublishResult>
                  <MessageId>567910cd-659e-55d4-8ccb-5aaf14679dc0</MessageId>
              </PublishResult>
              <ResponseMetadata>
                  <RequestId>d74b8436-ae13-5ab4-a9ff-ce54dfea72a0</RequestId>
              </ResponseMetadata>
          </PublishResponse>
      """
    "Sqs"     | "CreateQueue"       | "POST" | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsClient.builder()     | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }              | {
      if (!Boolean.getBoolean("testLatestDeps")) {
        def content = """
            <CreateQueueResponse>
                <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
                <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
            </CreateQueueResponse>
            """
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, content)
      }
      def content = """
          {
            "QueueUrl":"https://queue.amazonaws.com/123456789012/MyQueue"
          }
          """
      ResponseHeaders headers = ResponseHeaders.builder(HttpStatus.OK)
          .contentType(MediaType.PLAIN_TEXT_UTF_8)
          .add("x-amzn-RequestId", "7a62c49f-347e-4fc4-9331-6e8e7a96aa73")
          .build()
      return HttpResponse.of(headers, HttpData.of(StandardCharsets.UTF_8, content))
    }
    "Sqs"     | "SendMessage"       | "POST" | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsClient.builder()     | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("").build()) } | {
      if (!Boolean.getBoolean("testLatestDeps")) {
        def content = """
          <SendMessageResponse>
              <SendMessageResult>
                  <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                  <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                  <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
              </SendMessageResult>
              <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
          </SendMessageResponse>
          """
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, content)
      }
      def content = """
          {
            "MD5OfMessageBody":"d41d8cd98f00b204e9800998ecf8427e",
            "MD5OfMessageAttributes":"3ae8f24a165a8cedc005670c81a27295",
            "MessageId":"5fea7756-0ea4-451a-a703-a558b933e274"
          }
          """
      ResponseHeaders headers = ResponseHeaders.builder(HttpStatus.OK)
          .contentType(MediaType.PLAIN_TEXT_UTF_8)
          .add("x-amzn-RequestId", "27daac76-34dd-47df-bd01-1f6e873584a0")
          .build()
      return HttpResponse.of(headers, HttpData.of(StandardCharsets.UTF_8, content))
    }
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

    if (body instanceof Closure) {
      server.enqueue(body.call())
    } else {
      server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))
    }

    def response = call.call(client)
    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name operation != "SendMessage" ? "$service.$operation" : "somequeue publish"
          kind operation != "SendMessage" ? CLIENT : PRODUCER
          hasNoParent()
          attributes {
            if (service == "S3") {
              // Starting with AWS SDK V2 2.18.0, the s3 sdk will prefix the hostname with the bucket name in case
              // the bucket name is a valid DNS label, even in the case that we are using an endpoint override.
              // Previously the sdk was only doing that if endpoint had "s3" as label in the FQDN.
              // Our test assert both cases so that we don't need to know what version is being tested.
              "$ServerAttributes.SERVER_ADDRESS" { it == "somebucket.localhost" || it == "localhost" }
              "$UrlAttributes.URL_FULL" { it.startsWith("http://somebucket.localhost:${server.httpPort()}") || it.startsWith("http://localhost:${server.httpPort()}") }
            } else {
              "$ServerAttributes.SERVER_ADDRESS" "localhost"
              "$UrlAttributes.URL_FULL" { it == "http://localhost:${server.httpPort()}" || it == "http://localhost:${server.httpPort()}/" }
            }
            "$ServerAttributes.SERVER_PORT" server.httpPort()
            "$HttpAttributes.HTTP_REQUEST_METHOD" "$method"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$RpcIncubatingAttributes.RPC_SYSTEM" "aws-api"
            "$RpcIncubatingAttributes.RPC_SERVICE" "$service"
            "$RpcIncubatingAttributes.RPC_METHOD" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" QUEUE_URL
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "somequeue"
              "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
              "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
              "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            } else if (service == "Sns") {
              "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "somearn"
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
    "S3"    | "CreateBucket"      | "PUT"  | "UNKNOWN"                              | s3AsyncClientBuilder()   | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }                                              | ""
    "S3"    | "GetObject"         | "GET"  | "UNKNOWN"                              | s3AsyncClientBuilder()   | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build(), AsyncResponseTransformer.toBytes()) } | "1234567890"
    // Kinesis seems to expect an http2 response which is incompatible with our test server.
    // "Kinesis"  | "DeleteStream"      | "POST" | "/"                   | "UNKNOWN"                              | KinesisAsyncClient.builder()  | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }                                          | ""
    "Sqs"   | "CreateQueue"       | "POST" | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsAsyncClient.builder() | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }                                              | {
      if (!Boolean.getBoolean("testLatestDeps")) {
        def content = """
            <CreateQueueResponse>
                <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
                <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
            </CreateQueueResponse>
            """
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, content)
      }
      def content = """
          {
            "QueueUrl":"https://queue.amazonaws.com/123456789012/MyQueue"
          }
          """
      ResponseHeaders headers = ResponseHeaders.builder(HttpStatus.OK)
          .contentType(MediaType.PLAIN_TEXT_UTF_8)
          .add("x-amzn-RequestId", "7a62c49f-347e-4fc4-9331-6e8e7a96aa73")
          .build()
      return HttpResponse.of(headers, HttpData.of(StandardCharsets.UTF_8, content))
    }
    "Sqs"   | "SendMessage"       | "POST" | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsAsyncClient.builder() | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("").build()) }                                 | {
      if (!Boolean.getBoolean("testLatestDeps")) {
        def content = """
          <SendMessageResponse>
              <SendMessageResult>
                  <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                  <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                  <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
              </SendMessageResult>
              <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
          </SendMessageResponse>
          """
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, content)
      }
      def content = """
          {
            "MD5OfMessageBody":"d41d8cd98f00b204e9800998ecf8427e",
            "MD5OfMessageAttributes":"3ae8f24a165a8cedc005670c81a27295",
            "MessageId":"5fea7756-0ea4-451a-a703-a558b933e274"
          }
          """
      ResponseHeaders headers = ResponseHeaders.builder(HttpStatus.OK)
          .contentType(MediaType.PLAIN_TEXT_UTF_8)
          .add("x-amzn-RequestId", "27daac76-34dd-47df-bd01-1f6e873584a0")
          .build()
      return HttpResponse.of(headers, HttpData.of(StandardCharsets.UTF_8, content))
    }
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
    "Sns"   | "Publish"           | "POST" | "f187a3c1-376f-11df-8963-01868b7c937a" | SnsAsyncClient.builder() | { SnsAsyncClient c -> c.publish(r -> r.message("hello").topicArn("somearn")) }                                                   | """
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
    def builder = S3Client.builder()
        .overrideConfiguration(createOverrideConfigurationBuilder()
            .retryPolicy(RetryPolicy.builder().numRetries(1).build())
            .build())
        .endpointOverride(clientUri)
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .httpClientBuilder(ApacheHttpClient.builder().socketTimeout(Duration.ofMillis(50)))

    if (Boolean.getBoolean("testLatestDeps")) {
      builder.forcePathStyle(true)
    }

    def client = builder.build()

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
            "$ServerAttributes.SERVER_ADDRESS" { it == "somebucket.localhost" || it == "localhost" }
            "$UrlAttributes.URL_FULL" { it == "http://somebucket.localhost:${server.httpPort()}/somekey" || it == "http://localhost:${server.httpPort()}/somebucket/somekey" }
            "$ServerAttributes.SERVER_PORT" server.httpPort()
            "$HttpAttributes.HTTP_REQUEST_METHOD" "GET"
            "$RpcIncubatingAttributes.RPC_SYSTEM" "aws-api"
            "$RpcIncubatingAttributes.RPC_SERVICE" "S3"
            "$RpcIncubatingAttributes.RPC_METHOD" "GetObject"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "somebucket"
          }
        }
      }
    }
  }
}
