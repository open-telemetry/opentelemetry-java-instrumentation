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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class Aws2AsyncCancellationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final MockWebServerExtension server = new MockWebServerExtension();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void cancelAbortsInFlightRequest() throws Exception {
    // older sdk versions don't abort the request when the returned future is cancelled
    assumeTrue(testLatestDeps());

    // a single connection, so the second call can only proceed after the first one is aborted
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

    server.enqueue(HttpResponse.delayed(listBucketsResponse(), Duration.ofSeconds(30)));
    server.enqueue(listBucketsResponse());

    CompletableFuture<?> first = client.listBuckets();
    assertThat(server.takeRequest(10, SECONDS)).isNotNull();
    first.cancel(true);

    assertThat(client.listBuckets().get(10, SECONDS).buckets()).isEmpty();
  }

  private static HttpResponse listBucketsResponse() {
    return HttpResponse.of(
        HttpStatus.OK,
        MediaType.XML_UTF_8,
        "<ListAllMyBucketsResult><Buckets/></ListAllMyBucketsResult>");
  }
}
