package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.RecordedRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractAws2ClientTest extends AbstractAws2ClientCoreTest {
  private static final String QUEUE_URL = "http://xxx/somequeue";

  private void assumeSupportedConfig(String service, String operation) {
    Assumptions.assumeFalse(
        service.equals("Sqs")
            && operation.equals("SendMessage")
            && isSqsAttributeInjectionEnabled(),
        "Cannot check Sqs.SendMessage here due to hard-coded MD5.");
  }

  // Force localhost instead of relying on mock server because using ip is yet another corner case of the virtual
  // bucket changes introduced by aws sdk v2.18.0. When using IP, there is no way to prefix the hostname with the
  // bucket name as label.
  URI clientUri = URI.create("http://localhost:" + server.httpPort());

  S3ClientBuilder s3ClientBuilder() throws Exception {
    S3ClientBuilder builder = S3Client.builder();
    if (Boolean.getBoolean("testLatestDeps")) {
      Method forcePathStyleMethod = S3ClientBuilder.class.getMethod("forcePathStyle",
          Boolean.class);
      forcePathStyleMethod.invoke(true);
    }
    return builder;
  }

  S3AsyncClientBuilder s3AsyncClientBuilder() throws Exception {
    S3AsyncClientBuilder builder = S3AsyncClient.builder();
    if (Boolean.getBoolean("testLatestDeps")) {
      Method forcePathStyleMethod = S3ClientBuilder.class.getMethod("forcePathStyle",
          Boolean.class);
      forcePathStyleMethod.invoke(true);
    }
    return builder;
  }

  private static Stream<Arguments> provideS3Arguments() {
    return Stream.of(
        Arguments.of(
            "CreateBucket",
            "PUT",
            (Function<S3Client, Object>) c -> c.createBucket(
                CreateBucketRequest.builder().bucket("somebucket").build())
        ),
        Arguments.of("GetObject",
            "GET",
            (Function<S3Client, Object>) c -> c.getObject(
                GetObjectRequest.builder().bucket("somebucket").key("somekey").build())
        ));
  }

  @ParameterizedTest
  @MethodSource("provideS3Arguments")
  void testS3SendOperationRequestWithBuilder(String operation, String method,
      Function<S3Client, Object> call) throws Exception {

    S3ClientBuilder builder = s3ClientBuilder();
    configureSdkClient(builder);

    S3Client client = builder
        .endpointOverride(clientUri)
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    assertThat(response).isNotNull();
    assertThat(response.getClass().getSimpleName()).startsWith(operation);

    RecordedRequest request = server.takeRequest();
    assertThat(request).isNotNull();
    assertThat(request.request().headers().get("X-Amzn-Trace-Id")).isNotNull();
    assertThat(request.request().headers().get("traceparent")).isNotNull();

    getTesting().waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("S3." + operation)
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    satisfies(SERVER_ADDRESS,
                        v -> v.matches("somebucket.localhost|localhost")),
                    satisfies(URL_FULL,
                        val -> val.satisfiesAnyOf(
                            v -> assertThat(v).startsWith("http://somebucket.localhost:" + server.httpPort()),
                            v -> assertThat(v).startsWith("http://localhost:" + server.httpPort() + "/somebucket")
                        )),
                    equalTo(SERVER_PORT, server.httpPort()),
                    equalTo(HTTP_REQUEST_METHOD, method),
                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                    equalTo(RPC_SYSTEM, "aws-api"),
                    equalTo(RPC_SERVICE, "S3"),
                    equalTo(RPC_METHOD, operation),
                    equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                    equalTo(AWS_REQUEST_ID, "UNKOWN"),
                    equalTo(stringKey("aws.bucket.name"), "somebucket"))));


  }


}
