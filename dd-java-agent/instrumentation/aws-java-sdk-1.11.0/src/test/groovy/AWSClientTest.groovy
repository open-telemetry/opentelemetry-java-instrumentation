import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.ClientConfiguration
import com.amazonaws.Request
import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.SdkClientException
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.regions.Regions
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.execchain.RequestAbortedException
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT

class AWSClientTest extends AgentTestRunner {

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
  def credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())
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

    when:
    def response = call.call(client)

    then:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.size() == handlerCount
    client.requestHandler2s.get(0).getClass().getSimpleName() == "TracingRequestHandler"

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
            "$Tags.HTTP_URL.key" "$server.address/"
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" { it.contains(service) }
            "aws.endpoint" "$server.address"
            "aws.operation" "${operation}Request"
            "aws.agent" "java-aws-sdk"
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request"
          resourceName "$method /$url"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          childOf(span(0))
          tags {
            "$Tags.COMPONENT.key" "apache-httpclient"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/$url"
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
    service | operation           | method | url                  | handlerCount | call                                                                   | body               | client
    "S3"    | "CreateBucket"      | "PUT"  | "testbucket/"        | 1            | { client -> client.createBucket("testbucket") }                        | ""                 | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "S3"    | "GetObject"         | "GET"  | "someBucket/someKey" | 1            | { client -> client.getObject("someBucket", "someKey") }                | ""                 | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "EC2"   | "AllocateAddress"   | "POST" | ""                   | 4            | { client -> client.allocateAddress() }                                 | """
            <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
               <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId> 
               <publicIp>192.0.2.1</publicIp>
               <domain>standard</domain>
            </AllocateAddressResponse>
            """ | AmazonEC2ClientBuilder.standard().withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    "RDS"   | "DeleteOptionGroup" | "POST" | ""                   | 5            | { client -> client.deleteOptionGroup(new DeleteOptionGroupRequest()) } | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """       | AmazonRDSClientBuilder.standard().withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
  }

  def "send #operation request to closed port"() {
    setup:
    responseBody.set(body)

    when:
    call.call(client)

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "$service.$operation"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_URL.key" "http://localhost:${UNUSABLE_PORT}/"
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" { it.contains(service) }
            "aws.endpoint" "http://localhost:${UNUSABLE_PORT}"
            "aws.operation" "${operation}Request"
            "aws.agent" "java-aws-sdk"
            errorTags SdkClientException, ~/Unable to execute HTTP request/
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request"
          resourceName "$method /$url"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
          childOf(span(0))
          tags {
            "$Tags.COMPONENT.key" "apache-httpclient"
            "$Tags.HTTP_URL.key" "http://localhost:${UNUSABLE_PORT}/$url"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" UNUSABLE_PORT
            "$Tags.HTTP_METHOD.key" "$method"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            errorTags HttpHostConnectException, ~/Connection refused/
            defaultTags()
          }
        }
      }
    }

    where:
    service | operation   | method | url                  | call                                                    | body | client
    "S3"    | "GetObject" | "GET"  | "someBucket/someKey" | { client -> client.getObject("someBucket", "someKey") } | ""   | new AmazonS3Client(CREDENTIALS_PROVIDER_CHAIN, new ClientConfiguration().withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0))).withEndpoint("http://localhost:${UNUSABLE_PORT}")
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
    ((Tracer) TEST_TRACER).activeSpan() == null
    thrown RuntimeException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "java-aws-sdk"
          operationName "aws.http"
          resourceName "S3.HeadBucket"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-aws-sdk"
            "$Tags.HTTP_URL.key" "https://s3.amazonaws.com/"
            "$Tags.HTTP_METHOD.key" "HEAD"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" "Amazon S3"
            "aws.endpoint" "https://s3.amazonaws.com"
            "aws.operation" "HeadBucketRequest"
            "aws.agent" "java-aws-sdk"
            errorTags RuntimeException, "bad handler"
            defaultTags()
          }
        }
      }
    }
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
    AmazonS3Client client = new AmazonS3Client(new ClientConfiguration().withRequestTimeout(50 /* ms */))
      .withEndpoint("http://localhost:$server.address.port")

    when:
    client.getObject("someBucket", "someKey")

    then:
    ((Tracer) TEST_TRACER).activeSpan() == null
    thrown AmazonClientException

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
            "$Tags.HTTP_URL.key" "$server.address/"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "aws.service" "Amazon S3"
            "aws.endpoint" "$server.address"
            "aws.operation" "GetObjectRequest"
            "aws.agent" "java-aws-sdk"
            try {
              errorTags AmazonClientException, ~/Unable to execute HTTP request/
            } catch (AssertionError e) {
              errorTags SdkClientException, "Unable to execute HTTP request: Request did not complete before the request timeout configuration."
            }
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
              try {
                errorTags SocketException, "Socket closed"
              } catch (AssertionError e) {
                errorTags RequestAbortedException, "Request aborted"
              }
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
