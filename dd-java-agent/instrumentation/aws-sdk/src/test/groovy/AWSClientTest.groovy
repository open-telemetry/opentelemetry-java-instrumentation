import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import ratpack.http.Headers
import spock.lang.Timeout

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

@Timeout(20)
class AWSClientTest extends AgentTestRunner {

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

  def "send request with mocked back end"() {
    setup:
    def receivedHeaders = new AtomicReference<Headers>()
    def server = ratpack {
      handlers {
        all {
          receivedHeaders.set(request.headers)
          response.status(200).send("pong")
        }
      }
    }
    AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:$server.address.port", "us-west-2")

    AmazonWebServiceClient client = AmazonS3ClientBuilder
      .standard()
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(endpoint)
      .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
      .build()

    def bucket = client.createBucket("testbucket")

    expect:
    bucket != null

    client.requestHandler2s != null
    client.requestHandler2s.size() == 1
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

    TEST_WRITER.size() == 2

    def trace = TEST_WRITER.get(0)
    trace.size() == 2

    and: // span 0 - from apache-httpclient instrumentation
    def span1 = trace[0]

    span1.context().operationName == "PUT"
    span1.serviceName == "unnamed-java-app"
    span1.resourceName == "PUT"
    span1.type == null
    !span1.context().getErrorFlag()
    span1.context().parentId == 0


    def tags1 = span1.context().tags
    tags1["component"] == "apache-httpclient"
    tags1["thread.name"] != null
    tags1["thread.id"] != null
    tags1.size() == 3

    and: // span 1 - from aws instrumentation
    def span2 = trace[1]

    span2.context().operationName == "PUT"
    span2.serviceName == "unnamed-java-app"
    span2.resourceName == "PUT /testbucket/"
    span2.type == "http"
    !span2.context().getErrorFlag()
    span2.context().parentId == span1.spanId


    def tags2 = span2.context().tags
    tags2[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_CLIENT
    tags2[Tags.HTTP_METHOD.key] == "PUT"
    tags2[Tags.HTTP_URL.key] == "http://localhost:$server.address.port/testbucket/"
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
    span.resourceName == "PUT "
    span.type == "web"
    !span.context().getErrorFlag()
    span.context().parentId == 0

    def tags = span.context().tags
    tags[Tags.COMPONENT.key] == "java-aws-sdk"
    tags[Tags.SPAN_KIND.key] == Tags.SPAN_KIND_CLIENT
    tags[Tags.HTTP_METHOD.key] == "PUT"
    tags[Tags.HTTP_URL.key] == "http://localhost:$server.address.port"
    tags[Tags.HTTP_STATUS.key] == 200
    tags["aws.service"] == "Amazon S3"
    tags["aws.endpoint"] == "http://localhost:$server.address.port"
    tags["aws.operation"] == "CreateBucketRequest"
    tags["aws.agent"] == "java-aws-sdk"
    tags["params"] == "{}"
    tags["span.type"] == "web"
    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 13

    receivedHeaders.get().get("x-datadog-trace-id") == "$span.traceId"
    receivedHeaders.get().get("x-datadog-parent-id") == "$span.spanId"

    cleanup:
    server.close()
  }
}
