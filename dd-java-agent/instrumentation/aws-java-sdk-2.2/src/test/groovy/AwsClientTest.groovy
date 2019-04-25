import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.rds.RdsAsyncClient
import software.amazon.awssdk.services.rds.RdsClient
import software.amazon.awssdk.services.rds.model.DeleteOptionGroupRequest
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

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

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "$service.$operation"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${server.address}${path}"
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" "$service"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request"
          resourceName "$method $path"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          childOf(span(0))
          tags {
            "$Tags.COMPONENT.key" "apache-httpclient"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${server.address}${path}"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
    server.lastRequest.headers.get("x-datadog-trace-id") == null
    server.lastRequest.headers.get("x-datadog-parent-id") == null

    where:
    service | operation           | method | path                  | requestId                              | call                                                                                         | body               | builder
    "S3"    | "CreateBucket"      | "PUT"  | "/testbucket"         | "UNKNOWN"                              | { c -> c.createBucket(CreateBucketRequest.builder().bucket("testbucket").build()) }          | ""                 | S3Client.builder()
    "S3"    | "GetObject"         | "GET"  | "/someBucket/someKey" | "UNKNOWN"                              | { c -> c.getObject(GetObjectRequest.builder().bucket("someBucket").key("someKey").build()) } | ""                 | S3Client.builder()
    "Ec2"   | "AllocateAddress"   | "POST" | "/"                   | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | { c -> c.allocateAddress() }                                                                 | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """ | Ec2Client.builder()
    "Rds"   | "DeleteOptionGroup" | "POST" | "/"                   | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                     | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """       | RdsClient.builder()
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

    // Order is not guaranteed in these traces, so reorder them if needed to put aws trace first
    if (TEST_WRITER[0][0].serviceName != "java-aws-sdk") {
      def tmp = TEST_WRITER[0]
      TEST_WRITER[0] = TEST_WRITER[1]
      TEST_WRITER[1] = tmp
    }

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "$service.$operation"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${server.address}${path}"
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" "$service"
            "aws.operation" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            defaultTags()
          }
        }
      }
      // TODO: this should be part of the same trace but netty instrumentation doesn't cooperate
      trace(1, 1) {
        span(0) {
          operationName "netty.client.request"
          resourceName "$method $path"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${server.address}${path}"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
    server.lastRequest.headers.get("x-datadog-trace-id") == null
    server.lastRequest.headers.get("x-datadog-parent-id") == null

    where:
    service | operation           | method | path                  | requestId                              | call                                                                                                                             | body               | builder
    "S3"    | "CreateBucket"      | "PUT"  | "/testbucket"         | "UNKNOWN"                              | { c -> c.createBucket(CreateBucketRequest.builder().bucket("testbucket").build()) }                                              | ""                 | S3AsyncClient.builder()
    "S3"    | "GetObject"         | "GET"  | "/someBucket/someKey" | "UNKNOWN"                              | { c -> c.getObject(GetObjectRequest.builder().bucket("someBucket").key("someKey").build(), AsyncResponseTransformer.toBytes()) } | "1234567890"       | S3AsyncClient.builder()
    "Ec2"   | "AllocateAddress"   | "POST" | "/"                   | "59dbff89-35bd-4eac-99ed-be587EXAMPLE" | { c -> c.allocateAddress() }                                                                                                     | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """ | Ec2AsyncClient.builder()
    "Rds"   | "DeleteOptionGroup" | "POST" | "/"                   | "0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99" | { c -> c.deleteOptionGroup(DeleteOptionGroupRequest.builder().build()) }                                                         | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """       | RdsAsyncClient.builder()
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
    client.getObject(GetObjectRequest.builder().bucket("someBucket").key("someKey").build())

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "S3.GetObject"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_URL.key" "$server.address/someBucket/someKey"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" "S3"
            "aws.operation" "GetObject"
            "aws.agent" "java-aws-sdk"
            errorTags SdkClientException, "Unable to execute HTTP request: Read timed out"
            defaultTags()
          }
        }
        (1..4).each {
          span(it) {
            operationName "http.request"
            resourceName "GET /someBucket/someKey"
            spanType DDSpanTypes.HTTP_CLIENT
            errored true
            childOf(span(0))
            tags {
              "$Tags.COMPONENT.key" "apache-httpclient"
              "$Tags.HTTP_URL.key" "$server.address/someBucket/someKey"
              "$Tags.PEER_HOSTNAME.key" "localhost"
              "$Tags.PEER_PORT.key" server.address.port
              "$Tags.HTTP_METHOD.key" "GET"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              errorTags SocketTimeoutException, "Read timed out"
              defaultTags()
            }
          }
        }
      }
    }

    cleanup:
    server.close()
  }
}
