/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public abstract class AbstractAws2LambdaTest {

  @RegisterExtension
  private static final MockWebServerExtension server = new MockWebServerExtension();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  protected abstract InstrumentationExtension getTesting();

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected boolean canTestLambdaInvoke() {
    return true;
  }

  @Test
  void testInvokeLambda() {
    Assumptions.assumeTrue(canTestLambdaInvoke());

    LambdaClientBuilder builder = LambdaClient.builder();
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(server.httpUri());
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
    LambdaClient client = builder.build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "ok"));

    InvokeRequest request = InvokeRequest.builder().functionName("test").build();
    InvokeResponse response = client.invoke(request);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.payload().asUtf8String()).isEqualTo("ok");

    String clientContextHeader =
        server.takeRequest().request().headers().get("x-amz-client-context");
    assertThat(clientContextHeader).isNotEmpty();
    String clientContextJson =
        new String(Base64.getDecoder().decode(clientContextHeader), StandardCharsets.UTF_8);
    assertThat(clientContextJson).contains("traceparent");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Lambda.Invoke").hasKind(SpanKind.CLIENT).hasNoParent()));
  }
}
