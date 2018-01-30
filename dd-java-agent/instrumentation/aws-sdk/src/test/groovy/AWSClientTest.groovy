import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import ratpack.http.Headers

import java.util.concurrent.atomic.AtomicReference

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class AWSClientTest extends AgentTestRunner {

  def "request handler is hooked up"() {
    setup:
    def client = AmazonS3ClientBuilder.standard()
      .withRegion(Regions.US_EAST_1)
    client.build()

    expect:
    client.getRequestHandlers() != null
    client.getRequestHandlers().size() == 1
    client.getRequestHandlers().get(0).getClass().getSimpleName() == "TracingRequestHandler"
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
    AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:$server.address.port", "us-west-2");

    def client = AmazonS3ClientBuilder
      .standard()
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(endpoint)
      .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
      .build()

    def bucket = client.createBucket("testbucket")

    expect:
    bucket != null

    receivedHeaders.get().get("x-datadog-trace-id") == null
    receivedHeaders.get().get("x-datadog-parent-id") == null

    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
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
    span2.resourceName == "PUT"
    span2.type == null
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
    tags2.size() == 8

    cleanup:
    server.close()
  }
}
