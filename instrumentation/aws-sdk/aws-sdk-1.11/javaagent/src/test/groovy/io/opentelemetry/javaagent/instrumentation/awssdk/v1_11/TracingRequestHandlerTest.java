/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.testing.util.TraceUtils.withClientSpan;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public class TracingRequestHandlerTest {

  private TracingRequestHandler underTest;
  private Request<SendMessageRequest> request;

  @Before
  public void setUp() {
    this.underTest = new TracingRequestHandler();
    this.request = request();
  }

  @NotNull
  private Response<SendMessageResult> response() {
    return new Response<>(new SendMessageResult(), new HttpResponse(request, new HttpGet()));
  }

  private Request<SendMessageRequest> request() {
    Request<SendMessageRequest> request = new DefaultRequest<>(new SendMessageRequest(), "test");
    request.setEndpoint(URI.create("http://test.uri"));
    return request;
  }

  @Test
  public void shouldNotSetScopeAndNotFailIfClientSpanAlreadyPresent() {
    // given
    withClientSpan(
        "test",
        () -> {
          // when
          underTest.beforeRequest(request);
          // then - no exception and scope not set
          assertThat(request.getHandlerContext(TracingRequestHandler.SCOPE)).isNull();
          underTest.afterResponse(request, response());
        });
  }

  @Test
  public void shouldSetScopeIfClientSpanNotPresent() {
    // given
    // when
    underTest.beforeRequest(request);
    // then - no exception and scope not set
    assertThat(request.getHandlerContext(TracingRequestHandler.SCOPE)).isNotNull();
    underTest.afterResponse(request, response());
  }
}
