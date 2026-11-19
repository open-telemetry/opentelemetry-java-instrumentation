/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

class Aws2AsyncClientCancellationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final MockWebServerExtension server = new MockWebServerExtension();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void resetServer() {
    server.beforeTestExecution(null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void cancellingReturnedFutureAbortsRequest(boolean mayInterruptIfRunning)
      throws InterruptedException, ExecutionException, TimeoutException {
    // sdk 2.2.0 does not abort the request when the returned future is cancelled
    assumeTrue(testLatestDeps());

    S3AsyncClient client = createClient();
    // the first response never arrives, so the first request keeps the only connection
    server.enqueue(HttpResponse.streaming());
    server.enqueue(
        HttpResponse.of(
            HttpStatus.OK,
            MediaType.XML_UTF_8,
            "<ListAllMyBucketsResult><Buckets/></ListAllMyBucketsResult>"));

    CompletableFuture<ListBucketsResponse> first = client.listBuckets();
    assertThat(server.takeRequest(10, SECONDS)).isNotNull();

    first.cancel(mayInterruptIfRunning);

    // succeeds only when cancelling the first call released its connection
    assertThat(client.listBuckets().get(10, SECONDS)).isNotNull();
  }

  @Test
  void cancellingDependentFutureDoesNotAbortRequest() {
    S3AsyncClient client = createClient();
    server.enqueue(HttpResponse.streaming());

    CompletableFuture<ListBucketsResponse> first = client.listBuckets();
    assertThat(server.takeRequest(10, SECONDS)).isNotNull();

    CompletableFuture<ListBucketsResponse> dependent = first.thenApply(response -> response);
    dependent.cancel(true);

    assertThat(dependent).isCancelled();
    assertThat(first).isNotDone();
  }

  private static S3AsyncClient createClient() {
    SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder().maxConcurrency(1).build();
    cleanup.deferCleanup(httpClient);
    S3AsyncClient client =
        S3AsyncClient.builder()
            .httpClient(httpClient)
            .endpointOverride(server.httpUri())
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret")))
            .build();
    cleanup.deferCleanup(client);
    return client;
  }
}
