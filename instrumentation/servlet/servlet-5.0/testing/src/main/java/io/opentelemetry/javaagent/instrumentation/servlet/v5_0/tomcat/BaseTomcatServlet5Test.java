/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import jakarta.servlet.Servlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedClass
@ValueSource(
    classes = {TestServlet5.Sync.class, TestServlet5.Async.class, TestServlet5.FakeAsync.class})
public abstract class BaseTomcatServlet5Test extends AbstractTomcatServlet5Test {

  @Parameter private Class<? extends Servlet> servletClass;

  @Override
  public void startServer() {}

  @BeforeParameterizedClassInvocation
  public void startServerParameterized() {
    super.startServer();
  }

  @AfterParameterizedClassInvocation
  void cleanup() {
    cleanupServer();
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return servletClass;
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return servletClass != TestServlet5.Async.class;
  }

  @Test
  void startAsyncInSpan() {
    assumeTrue(servletClass == TestServlet5.Async.class);

    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            HttpMethod.GET, resolveAddress(SUCCESS, "h1c://") + "?startAsyncInSpan=true");
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(isAgentTest() ? "GET " + getContextPath() + "/success" : "GET")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent(),
                    span ->
                        span.hasName("startAsync")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("controller")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }
}
