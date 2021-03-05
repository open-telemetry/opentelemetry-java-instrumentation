package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.instrumentation.testing.util.TraceUtils.withClientSpan;
import static io.opentelemetry.instrumentation.testing.util.TraceUtils.withSpan;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

@ExtendWith(MockitoExtension.class)
class RestTemplateInterceptorTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension instrumentation =
      LibraryInstrumentationExtension.create();

  @Mock HttpRequest httpRequestMock;
  @Mock HttpHeaders httpHeadersMock;
  @Mock ClientHttpRequestExecution requestExecutionMock;
  @Mock ClientHttpResponse httpResponseMock;
  byte[] requestBody = new byte[0];

  @Test
  void shouldSkipWhenContextHasClientSpan() throws Exception {
    // given
    RestTemplateInterceptor interceptor =
        new RestTemplateInterceptor(instrumentation.getOpenTelemetry());

    // when
    withClientSpan(
        "parent",
        () -> {
          interceptor.intercept(httpRequestMock, requestBody, requestExecutionMock);
        });

    // then
    then(requestExecutionMock).should().execute(httpRequestMock, requestBody);

    assertThat(instrumentation.waitForTraces(1))
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.CLIENT)));
  }

  @Test
  void shouldStartAndEndSpan() throws Exception {
    // given
    RestTemplateInterceptor interceptor =
        new RestTemplateInterceptor(instrumentation.getOpenTelemetry());

    given(httpRequestMock.getMethod()).willReturn(HttpMethod.GET);
    given(httpRequestMock.getHeaders()).willReturn(httpHeadersMock);

    given(requestExecutionMock.execute(httpRequestMock, requestBody)).willReturn(httpResponseMock);

    given(httpResponseMock.getStatusCode()).willReturn(HttpStatus.OK);

    // when
    ClientHttpResponse actual =
        withSpan(
            "parent",
            () -> interceptor.intercept(httpRequestMock, requestBody, requestExecutionMock));

    // then
    assertSame(httpResponseMock, actual);

    then(httpHeadersMock).should().set(eq("traceparent"), anyString());

    List<List<SpanData>> traces = instrumentation.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span -> span.hasName("HTTP GET").hasKind(SpanKind.CLIENT)));
  }

  @Test
  void shouldStartAndEndSpanWithException() throws Exception {
    // given
    RestTemplateInterceptor interceptor =
        new RestTemplateInterceptor(instrumentation.getOpenTelemetry());

    given(httpRequestMock.getMethod()).willReturn(HttpMethod.GET);
    given(httpRequestMock.getHeaders()).willReturn(httpHeadersMock);

    Exception thrown = new IOException("boom");
    given(requestExecutionMock.execute(httpRequestMock, requestBody)).willThrow(thrown);

    // when
    IOException actual =
        assertThrows(
            IOException.class,
            () ->
                withSpan(
                    "parent",
                    () ->
                        interceptor.intercept(httpRequestMock, requestBody, requestExecutionMock)));

    // then
    assertSame(thrown, actual);

    then(httpHeadersMock).should().set(eq("traceparent"), anyString());

    List<List<SpanData>> traces = instrumentation.waitForTraces(1);
    assertThat(traces)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    parentSpan -> parentSpan.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName("HTTP GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())));
  }
}
