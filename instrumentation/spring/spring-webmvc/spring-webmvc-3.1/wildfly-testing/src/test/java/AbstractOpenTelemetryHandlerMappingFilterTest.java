/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.URI;
import javax.servlet.ServletException;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(ArquillianExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RunAsClient
public abstract class AbstractOpenTelemetryHandlerMappingFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public final WebClient client = WebClient.of();

  @ArquillianResource public URI url;

  private String getAddress(String service) {
    return url.resolve(service).toString();
  }

  @Test
  public void testSuccess() {
    AggregatedHttpResponse response = client.get(getAddress("hello/world")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello world");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("/hello/{name}").hasKind(SpanKind.SERVER).hasNoParent(),
                span ->
                    span.hasName("HelloController.hello")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  public void testException() {
    AggregatedHttpResponse response = client.get(getAddress("hello/world")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("/hello/{name}")
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error())
                        .hasNoParent()
                        .hasException(new ServletException("exception"))));
  }
}
