import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.rds.AmazonRDSClient
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

class AWSClientTest extends AgentTestRunner {
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
    def response = call.call(client)

    expect:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.size() == handlerCount
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "http.request"
          resourceName "$method /$url"
          errored false
          parent() // FIXME: This should be a child of the aws.http call.
          tags {
            "$Tags.COMPONENT.key" "apache-httpclient"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/$url"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "$service.$operation"
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "aws.service" String
            "aws.endpoint" "$server.address"
            "aws.operation" "${operation}Request"
            "aws.agent" "java-aws-sdk"
            defaultTags()
          }
        }
      }
    }
    // Not sure why these are children of the aws.http span:
    server.lastRequest.headers.get("x-datadog-trace-id") == TEST_WRITER[1][0].traceId
    server.lastRequest.headers.get("x-datadog-parent-id") == TEST_WRITER[1][0].spanId

    where:
    service | operation           | method | url                  | handlerCount | call                                                                                                                                   | body               | client
    "S3"    | "CreateBucket"      | "PUT"  | "testbucket/"        | 1            | { client -> client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build()); client.createBucket("testbucket") } | ""                 | new AmazonS3Client().withEndpoint("http://localhost:$server.address.port")
    "S3"    | "GetObject"         | "GET"  | "someBucket/someKey" | 1            | { client -> client.getObject("someBucket", "someKey") }                                                                                | ""                 | new AmazonS3Client().withEndpoint("http://localhost:$server.address.port")
    "EC2"   | "AllocateAddress"   | "POST" | ""                   | 4            | { client -> client.allocateAddress() }                                                                                                 | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """ | new AmazonEC2Client().withEndpoint("http://localhost:$server.address.port")
    "RDS"   | "DeleteOptionGroup" | "POST" | ""                   | 1            | { client -> client.deleteOptionGroup(new DeleteOptionGroupRequest()) }                                                                 | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """       | new AmazonRDSClient().withEndpoint("http://localhost:$server.address.port")
  }
}
