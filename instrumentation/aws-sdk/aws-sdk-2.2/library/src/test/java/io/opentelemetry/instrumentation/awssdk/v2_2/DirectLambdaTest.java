/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DirectLambdaTest{
  private Context context;
  @Before
  public void setup() {
    OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

    Span parent = GlobalOpenTelemetry.getTracer("test").spanBuilder("parentSpan").setSpanKind(SpanKind.SERVER).startSpan();
    context =  parent.storeInContext(Context.current());
    assertThat(context.toString().equals("{}")).isFalse();
    parent.end();
  }

  @After
  public void cleanup() {
    GlobalOpenTelemetry.resetForTest();
  }

  private static String base64ify(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }



  @Test
  public void noExistingClientContext() throws Exception {
    InvokeRequest r = InvokeRequest.builder().build();

    InvokeRequest newR = (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);

    String newCC = newR.clientContext();
    newCC = new String(Base64.getDecoder().decode(newCC), StandardCharsets.UTF_8);
    assertThat(newCC.contains("traceparent")).isTrue();
  }

  @Test
  public void withExistingClientContext() throws Exception {
    String clientContext = base64ify("{\"otherStuff\": \"otherValue\", \"custom\": {\"preExisting\": \"somevalue\"} }");
    InvokeRequest r = InvokeRequest.builder().clientContext(clientContext).build();

    InvokeRequest newR = (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);

    String newCC = newR.clientContext();
    newCC = new String(Base64.getDecoder().decode(newCC), StandardCharsets.UTF_8);
    assertThat(newCC.contains("traceparent")).isTrue();
    assertThat(newCC.contains("preExisting")).isTrue();
    assertThat(newCC.contains("otherStuff")).isTrue();
  }

  @Test
  public void exceedingMaximumLengthDoesNotModify() throws Exception {
    // awkward way to build a valid json that is almost but not quite too long
    boolean continueLengthingInput = true;
    StringBuffer x = new StringBuffer("x");
    String base64edCC = "";
    while(continueLengthingInput) {
      x.append("x");
      String newCC = base64ify("{\""+x+"\": \""+x+"\"}");
      if (newCC.length() >= DirectLambdaImpl.MAX_CLIENT_CONTEXT_LENGTH) {
        continueLengthingInput = false;
        break;
      }
      base64edCC = newCC;
      continueLengthingInput = base64edCC.length() < DirectLambdaImpl.MAX_CLIENT_CONTEXT_LENGTH;
    }

    InvokeRequest r = InvokeRequest.builder().clientContext(base64edCC).build();
    assertThat(r.clientContext().equals(base64edCC)).isTrue();

    InvokeRequest newR = (InvokeRequest) DirectLambdaImpl.modifyOrAddCustomContextHeader(r, context);
    assertThat(newR == null).isTrue(); // null return means no modification performed
  }


}
