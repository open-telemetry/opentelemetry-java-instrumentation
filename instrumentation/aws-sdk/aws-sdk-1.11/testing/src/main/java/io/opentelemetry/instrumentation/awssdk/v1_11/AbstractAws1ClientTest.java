/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractAws1ClientTest {
  protected abstract InstrumentationExtension testing();

  private static final MockWebServerExtension server = new MockWebServerExtension();
  private static AwsClientBuilder.EndpointConfiguration endpoint;
  private static final AWSStaticCredentialsProvider credentialsProvider =
      new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());

  @BeforeAll
  public static void setUp() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key");
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key");
    server.start();
    endpoint = new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-west-2");
    server.beforeTestExecution(null);
  }

  @AfterAll
  public static void cleanUp() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY);
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY);
    server.stop();
  }

  //  abstract AwsClientBuilder<?> configureClient(AwsClientBuilder client);

  @Test
  @SuppressWarnings("unchecked")
  public void test() throws NoSuchFieldException, IllegalAccessException {
    String body = "";
    AmazonS3ClientBuilder clientBuilder =
        AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true);
    AmazonS3 client =
        (AmazonS3)
            configureClient(clientBuilder)
                .withEndpointConfiguration(endpoint)
                .withCredentials(credentialsProvider)
                .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));
    Bucket response = client.createBucket("testbucket");

    assertThat(response).isNotNull();

    Field requestHandler2sField = client.getClass().getDeclaredField("requestHandler2s");
    requestHandler2sField.setAccessible(true);
    List<RequestHandler2> requestHandler2s =
        (List<RequestHandler2>) requestHandler2sField.get(client);

    assertThat(requestHandler2s).isNotNull();
    assertThat(
            requestHandler2s.stream()
                .filter(h -> "TracingRequestHandler".equals(h.getClass().getSimpleName())))
        .isNotNull();
  }
}
