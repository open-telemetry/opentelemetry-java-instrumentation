/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.TracingRequestHandler;
import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracingRequestHandlerTest {

  private static Response<SendMessageResult> response(Request request) {
    return new Response<>(new SendMessageResult(), new HttpResponse(request, new HttpGet()));
  }

  private static Request<SendMessageRequest> request() {
    // Using a subclass of SendMessageRequest because for SendMessageRequest instrumentation
    // creates PRODUCER span, for others CLIENT span. We need to use CLIENT spans for
    // runWithClientSpan in shouldNotSetScopeAndNotFailIfClientSpanAlreadyPresent to work.
    class CustomSendMessageRequest extends SendMessageRequest {}

    Request<SendMessageRequest> request =
        new DefaultRequest<>(new CustomSendMessageRequest(), "test");
    request.setEndpoint(URI.create("http://test.uri"));
    return request;
  }

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void shouldNotSetScopeAndNotFailIfClientSpanAlreadyPresent() {
    // given
    TracingRequestHandler underTest = new TracingRequestHandler();
    Request<SendMessageRequest> request = request();

    testing.runWithClientSpan(
        "test",
        () -> {
          // when
          underTest.beforeRequest(request);
          // then - no exception and scope not set
          assertThat(request.getHandlerContext(TracingRequestHandler.SCOPE)).isNull();
          underTest.afterResponse(request, response(request));
        });
  }

  @Test
  void shouldSetScopeIfClientSpanNotPresent() {
    // given
    TracingRequestHandler underTest = new TracingRequestHandler();
    Request<SendMessageRequest> request = request();

    // when
    underTest.beforeRequest(request);
    // then - no exception and scope not set
    assertThat(request.getHandlerContext(TracingRequestHandler.SCOPE)).isNotNull();
    underTest.afterResponse(request, response(request));

    // cleanup
    underTest.afterError(request, null, null);
  }
}
