/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2LambdaTest;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

class Aws2LambdaTest extends AbstractAws2LambdaTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private static Context context;
  private static AwsSdkTelemetry telemetry;

  @BeforeAll
  static void setup() {
    testing.runWithHttpServerSpan(
        () -> {
          context = Context.current();
        });

    telemetry = AwsSdkTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(telemetry.newExecutionInterceptor());
  }

  private static String base64ify(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void noExistingClientContext() {
    InvokeRequest request = InvokeRequest.builder().build();

    InvokeRequest newRequest =
        (InvokeRequest) LambdaImpl.modifyOrAddCustomContextHeader(request, context);

    String newClientContext = newRequest.clientContext();
    newClientContext =
        new String(Base64.getDecoder().decode(newClientContext), StandardCharsets.UTF_8);
    assertThat(newClientContext.contains("traceparent")).isTrue();
  }

  @Test
  void withExistingClientContext() {
    String clientContext =
        base64ify(
            "{\"otherStuff\": \"otherValue\", \"custom\": {\"preExisting\": \"somevalue\"} }");
    InvokeRequest request = InvokeRequest.builder().clientContext(clientContext).build();

    InvokeRequest newRequest =
        (InvokeRequest) LambdaImpl.modifyOrAddCustomContextHeader(request, context);

    String newClientContext = newRequest.clientContext();
    newClientContext =
        new String(Base64.getDecoder().decode(newClientContext), StandardCharsets.UTF_8);
    assertThat(newClientContext.contains("traceparent")).isTrue();
    assertThat(newClientContext.contains("preExisting")).isTrue();
    assertThat(newClientContext.contains("otherStuff")).isTrue();
  }

  @Test
  void exceedingMaximumLengthDoesNotModify() {
    // awkward way to build a valid json that is almost but not quite too long
    StringBuilder buffer = new StringBuilder("x");
    String long64edClientContext = "";
    while (true) {
      buffer.append("x");
      String newClientContext = base64ify("{\"" + buffer + "\": \"" + buffer + "\"}");
      if (newClientContext.length() >= LambdaImpl.MAX_CLIENT_CONTEXT_LENGTH) {
        break;
      }
      long64edClientContext = newClientContext;
    }

    InvokeRequest request = InvokeRequest.builder().clientContext(long64edClientContext).build();
    assertThat(request.clientContext().equals(long64edClientContext)).isTrue();

    InvokeRequest newRequest =
        (InvokeRequest) LambdaImpl.modifyOrAddCustomContextHeader(request, context);
    assertThat(newRequest).isNull(); // null return means no modification performed
  }
}
