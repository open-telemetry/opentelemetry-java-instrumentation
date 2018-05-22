import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import ratpack.http.Headers
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

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
  def credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())
  @Shared
  def receivedHeaders = new AtomicReference<Headers>()
  @Shared
  def responseBody = new AtomicReference<String>()
  @Shared
  def server = ratpack {
    handlers {
      all {
        receivedHeaders.set(request.headers)
        response.status(200).send(responseBody.get())
      }
    }
  }
  @Shared
  def endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:$server.address.port", "us-west-2")

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

  def "send #operation request with mocked response"() {
    setup:
    responseBody.set(body)
    def response = call.call(client)

    expect:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.size() == handlerCount
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

    TEST_WRITER.size() == 2

    def trace = TEST_WRITER.get(0)
    trace.size() == 2

    and: // span 0 - from apache-httpclient instrumentation
    def span1 = trace[0]

    span1.context().operationName == "apache.http"
    span1.serviceName == "unnamed-java-app"
    span1.resourceName == "apache.http"
    span1.type == null
    !span1.context().getErrorFlag()
    span1.context().parentId == 0


    def tags1 = span1.context().tags
    tags1["component"] == "apache-httpclient"
    tags1["thread.name"] != null
    tags1["thread.id"] != null
    tags1.size() == 3

    and: // span 1 - from apache-httpclient instrumentation
    def span2 = trace[1]

    span2.context().operationName == "http.request"
    span2.serviceName == "unnamed-java-app"
    span2.resourceName == "$method /$url"
    span2.type == "http"
    !span2.context().getErrorFlag()
    span2.context().parentId == span1.spanId


    def tags2 = span2.context().tags
    tags2[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_CLIENT
    tags2[Tags.HTTP_METHOD.key] == "$method"
    tags2[Tags.HTTP_URL.key] == "http://localhost:$server.address.port/$url"
    tags2[Tags.PEER_HOSTNAME.key] == "localhost"
    tags2[Tags.PEER_PORT.key] == server.address.port
    tags2[DDTags.THREAD_NAME] != null
    tags2[DDTags.THREAD_ID] != null
    tags2.size() == 9

    and:

    def trace2 = TEST_WRITER.get(1)
    trace2.size() == 1

    and: // span 0 - from aws instrumentation
    def span = trace2[0]

    span.context().operationName == "aws.http"
    span.serviceName == "java-aws-sdk"
    span.resourceName == "$service.$operation"
    span.type == "web"
    !span.context().getErrorFlag()
    span.context().parentId == 0

    def tags = span.context().tags
    tags[Tags.COMPONENT.key] == "java-aws-sdk"
    tags[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_CLIENT
    tags[Tags.HTTP_METHOD.key] == "$method"
    tags[Tags.HTTP_URL.key] == "http://localhost:$server.address.port"
    tags[Tags.HTTP_STATUS.key] == 200
    tags["aws.service"] == "Amazon $service" || tags["aws.service"] == "Amazon$service"
    tags["aws.endpoint"] == "http://localhost:$server.address.port"
    tags["aws.operation"] == "${operation}Request"
    tags["aws.agent"] == "java-aws-sdk"
    tags["params"] == params
    tags["span.type"] == "web"
    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 13

    receivedHeaders.get().get("x-datadog-trace-id") == "$span.traceId"
    receivedHeaders.get().get("x-datadog-parent-id") == "$span.spanId"

    where:
    service | operation           | method | url                  | handlerCount | call                                                                   | body               | params                                              | client
    "S3"    | "CreateBucket"      | "PUT"  | "testbucket/"        | 1            | { client -> client.createBucket("testbucket") }                        | ""                 | "{}"                                                | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "S3"    | "GetObject"         | "GET"  | "someBucket/someKey" | 1            | { client -> client.getObject("someBucket", "someKey") }                | ""                 | "{}"                                                | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "EC2"   | "AllocateAddress"   | "POST" | ""                   | 4            | { client -> client.allocateAddress() }                                 | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """ | "{Action=[AllocateAddress],Version=[2016-11-15]}"   | AmazonEC2ClientBuilder.standard().withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "RDS"   | "DeleteOptionGroup" | "POST" | ""                   | 5            | { client -> client.deleteOptionGroup(new DeleteOptionGroupRequest()) } | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """       | "{Action=[DeleteOptionGroup],Version=[2014-10-31]}" | AmazonRDSClientBuilder.standard().withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
  }
}
