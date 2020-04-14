/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.trace.Span.Kind.CLIENT

class AwsClientTest extends AgentTestRunner {

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER = StaticCredentialsProvider
    .create(AwsBasicCredentials.create("my-access-key", "my-secret-key"))

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

  def "send #operation request with builder {#builder.class.getName()} mocked response"() {
    setup:
    def client = builder
      .endpointOverride(server.address)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    responseBody.set(body)
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null
    response.class.simpleName.startsWith(operation) || response instanceof ResponseInputStream

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "$service.$operation"
          spanKind CLIENT
          errored false
          parent()
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" { it.startsWith("${server.address}${path}") }
            "$Tags.HTTP_METHOD" "$method"
            "$Tags.HTTP_STATUS" 200
            "aws.service" "$service"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" "someurl"
            } else if (service == "DynamoDb") {
              "aws.table.name" "sometable"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            }
          }
        }
        span(1) {
          operationName expectedOperationName(method)
          spanKind CLIENT
          errored false
          childOf span(0)
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" { it.startsWith("${server.address}${path}") }
            "$Tags.HTTP_METHOD" "$method"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }
    server.lastRequest.headers.get("traceparent") == null

    where:
    service    | operation           | method | path                  | requestId                              | builder                  | call                                                                                             | body
    "S3"       | "CreateBucket"      | "PUT"  | "/somebucket"         | "UNKNOWN"                              | S3Client.builder()       | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }              | ""
    "S3"       | "GetObject"         | "GET"  | "/somebucket/somekey" | "UNKNOWN"                              | S3Client.builder()       | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build()) }     | ""
    "DynamoDb" | "CreateTable"       | "POST" | "/"                   | "UNKNOWN"                              | DynamoDbClient.builder() | { c -> c.createTable(CreateTableRequest.builder().tableName("sometable").build()) }              | ""
    "Kinesis"  | "DeleteStream"      | "POST" | "/"                   | "UNKNOWN"                              | KinesisClient.builder()  | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }          | ""
    "Sqs"      | "CreateQueue"       | "POST" | "/"                   | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsClient.builder()      | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }              | """
        <CreateQueueResponse>
            <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
            <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
        </CreateQueueResponse>
        """
    "Sqs"      | "SendMessage"       | "POST" | "/"                   | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsClient.builder()      | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl("someurl").messageBody("").build()) } | """
        <SendMessageResponse>
            <SendMessageResult>
                <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
            </SendMessageResult>
            <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
        </SendMessageResponse>
        """
    "Ec2"      | "AllocateAddress"   | "POST" | "/"                   | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | Ec2Client.builder()      | { c -> c.allocateAddress() }                                                                     | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
        """
    "Rds"      | "DeleteOptionGroup" | "POST" | "/"                   | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | RdsClient.builder()      | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                         | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata><RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId></ResponseMetadata>
        </DeleteOptionGroupResponse>
        """
  }

  def "send #operation async request with builder {#builder.class.getName()} mocked response"() {
    setup:
    def client = builder
      .endpointOverride(server.address)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    responseBody.set(body)
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "$service.$operation"
          spanKind CLIENT
          errored false
          parent()
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" { it.startsWith("${server.address}${path}") }
            "$Tags.HTTP_METHOD" "$method"
            "$Tags.HTTP_STATUS" 200
            "aws.service" "$service"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            if (service == "S3") {
              "aws.bucket.name" "somebucket"
            } else if (service == "Sqs" && operation == "CreateQueue") {
              "aws.queue.name" "somequeue"
            } else if (service == "Sqs" && operation == "SendMessage") {
              "aws.queue.url" "someurl"
            } else if (service == "DynamoDb") {
              "aws.table.name" "sometable"
            } else if (service == "Kinesis") {
              "aws.stream.name" "somestream"
            }
          }
        }
      }
      // TODO: this should be part of the same trace but netty instrumentation doesn't cooperate
      trace(1, 1) {
        span(0) {
          operationName expectedOperationName(method)
          spanKind CLIENT
          errored false
          parent()
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_IP" "127.0.0.1"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" { it.startsWith("${server.address}${path}") }
            "$Tags.HTTP_METHOD" "$method"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }
    server.lastRequest.headers.get("traceparent") == null

    where:
    service    | operation           | method | path                  | requestId                              | builder                       | call                                                                                                                             | body
    "S3"       | "CreateBucket"      | "PUT"  | "/somebucket"         | "UNKNOWN"                              | S3AsyncClient.builder()       | { c -> c.createBucket(CreateBucketRequest.builder().bucket("somebucket").build()) }                                              | ""
    "S3"       | "GetObject"         | "GET"  | "/somebucket/somekey" | "UNKNOWN"                              | S3AsyncClient.builder()       | { c -> c.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build(), AsyncResponseTransformer.toBytes()) } | "1234567890"
    "DynamoDb" | "CreateTable"       | "POST" | "/"                   | "UNKNOWN"                              | DynamoDbAsyncClient.builder() | { c -> c.createTable(CreateTableRequest.builder().tableName("sometable").build()) }                                              | ""
    // Kinesis seems to expect an http2 response which is incompatible with our test server.
    // "Kinesis"  | "DeleteStream"      | "POST" | "/"                   | "UNKNOWN"                              | KinesisAsyncClient.builder()  | { c -> c.deleteStream(DeleteStreamRequest.builder().streamName("somestream").build()) }                                          | ""
    "Sqs"      | "CreateQueue"       | "POST" | "/"                   | "7a62c49f-347e-4fc4-9331-6e8e7a96aa73" | SqsAsyncClient.builder()      | { c -> c.createQueue(CreateQueueRequest.builder().queueName("somequeue").build()) }                                              | """
        <CreateQueueResponse>
            <CreateQueueResult><QueueUrl>https://queue.amazonaws.com/123456789012/MyQueue</QueueUrl></CreateQueueResult>
            <ResponseMetadata><RequestId>7a62c49f-347e-4fc4-9331-6e8e7a96aa73</RequestId></ResponseMetadata>
        </CreateQueueResponse>
        """
    "Sqs"      | "SendMessage"       | "POST" | "/"                   | "27daac76-34dd-47df-bd01-1f6e873584a0" | SqsAsyncClient.builder()      | { c -> c.sendMessage(SendMessageRequest.builder().queueUrl("someurl").messageBody("").build()) }                                 | """
        <SendMessageResponse>
            <SendMessageResult>
                <MD5OfMessageBody>d41d8cd98f00b204e9800998ecf8427e</MD5OfMessageBody>
                <MD5OfMessageAttributes>3ae8f24a165a8cedc005670c81a27295</MD5OfMessageAttributes>
                <MessageId>5fea7756-0ea4-451a-a703-a558b933e274</MessageId>
            </SendMessageResult>
            <ResponseMetadata><RequestId>27daac76-34dd-47df-bd01-1f6e873584a0</RequestId></ResponseMetadata>
        </SendMessageResponse>
        """
    "Ec2"      | "AllocateAddress"   | "POST" | "/"                   | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | Ec2AsyncClient.builder()      | { c -> c.allocateAddress() }                                                                                                     | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
        """
    "Rds"      | "DeleteOptionGroup" | "POST" | "/"                   | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | RdsAsyncClient.builder()      | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                                                         | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata><RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId></ResponseMetadata>
        </DeleteOptionGroupResponse>
        """
  }

  def "timeout and retry errors captured"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          Thread.sleep(500)
          response.status(200).send()
        }
      }
    }
    def client = S3Client.builder()
      .endpointOverride(server.address)
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .httpClientBuilder(ApacheHttpClient.builder().socketTimeout(Duration.ofMillis(50)))
      .build()

    when:
    client.getObject(GetObjectRequest.builder().bucket("somebucket").key("somekey").build())

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          operationName "S3.GetObject"
          spanKind CLIENT
          errored true
          parent()
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$server.address/somebucket/somekey"
            "$Tags.HTTP_METHOD" "GET"
            "aws.service" "S3"
            "aws.operation" "GetObject"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "somebucket"
            errorTags SdkClientException, "Unable to execute HTTP request: Read timed out"
          }
        }
        (1..4).each {
          span(it) {
            operationName expectedOperationName("GET")
            spanKind CLIENT
            errored true
            childOf span(0)
            tags {
              "$MoreTags.NET_PEER_NAME" "localhost"
              "$MoreTags.NET_PEER_PORT" server.address.port
              "$Tags.HTTP_URL" "$server.address/somebucket/somekey"
              "$Tags.HTTP_METHOD" "GET"
              errorTags SocketTimeoutException, "Read timed out"
            }
          }
        }
      }
    }

    cleanup:
    server.close()
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : HttpClientDecorator.DEFAULT_SPAN_NAME
  }
}
