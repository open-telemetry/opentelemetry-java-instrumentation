/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

public class DirectLambdaTest {
  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Context context;

  @Before
  public void setup() {
    testing.runWithHttpServerSpan(
        () -> {
          context = Context.current();
        });
  }

  private static String base64ify(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void noExistingClientContext() throws Exception {
    InvokeRequest r = InvokeRequest.builder().build();

    InvokeRequest newR =
        (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);

    String newClientContext = newR.clientContext();
    newClientContext =
        new String(Base64.getDecoder().decode(newClientContext), StandardCharsets.UTF_8);
    assertThat(newClientContext.contains("traceparent")).isTrue();
  }

  @Test
  public void withExistingClientContext() throws Exception {
    String clientContext =
        base64ify(
            "{\"otherStuff\": \"otherValue\", \"custom\": {\"preExisting\": \"somevalue\"} }");
    InvokeRequest r = InvokeRequest.builder().clientContext(clientContext).build();

    InvokeRequest newR =
        (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);

    String newClientContext = newR.clientContext();
    newClientContext =
        new String(Base64.getDecoder().decode(newClientContext), StandardCharsets.UTF_8);
    assertThat(newClientContext.contains("traceparent")).isTrue();
    assertThat(newClientContext.contains("preExisting")).isTrue();
    assertThat(newClientContext.contains("otherStuff")).isTrue();
  }

  @Test
  public void exceedingMaximumLengthDoesNotModify() throws Exception {
    // awkward way to build a valid json that is almost but not quite too long
    boolean continueLengthingInput = true;
    StringBuffer x = new StringBuffer("x");
    String long64edClientContext = "";
    while (continueLengthingInput) {
      x.append("x");
      String newClientContext = base64ify("{\"" + x + "\": \"" + x + "\"}");
      if (newClientContext.length() >= DirectLambdaImpl.MAX_CLIENT_CONTEXT_LENGTH) {
        continueLengthingInput = false;
        break;
      }
      long64edClientContext = newClientContext;
      continueLengthingInput =
          long64edClientContext.length() < DirectLambdaImpl.MAX_CLIENT_CONTEXT_LENGTH;
    }

    InvokeRequest r = InvokeRequest.builder().clientContext(long64edClientContext).build();
    assertThat(r.clientContext().equals(long64edClientContext)).isTrue();

    InvokeRequest newR =
        (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);
    assertThat(newR == null).isTrue(); // null return means no modification performed
  }
}
