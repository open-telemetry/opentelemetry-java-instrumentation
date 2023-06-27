/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class SingleServiceCamelTest extends RetryOnAddressAlreadyInUse {

  private static final Logger logger = LoggerFactory.getLogger(SingleServiceCamelTest.class);

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext server;

  private static Integer port;

  private static final OkHttpClient client = new OkHttpClient();

  @BeforeAll
  public static void setupSpec() {
    withRetryOnAddressAlreadyInUse(SingleServiceCamelTest::setupSpecUnderRetry);
  }

  public static void setupSpecUnderRetry() {
    port = PortUtils.findOpenPort();
    SpringApplication app = new SpringApplication(SingleServiceConfig.class);
    app.setDefaultProperties(ImmutableMap.of("camelService.port", port));
    server = app.run();
    logger.info("http server started at: http://localhost:{}/", port);
  }

  @AfterAll
  public static void cleanupSpec() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  @Test
  public void singleCamelServiceSpan() throws IOException {
    String requestUrl = "http://localhost:" + port + "/camelService";

    Request request =
        new Request.Builder()
            .url(requestUrl)
            .post(RequestBody.create("testContent", MediaType.parse("text/plain")))
            .build();

    client.newCall(request).execute();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST /camelService")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(SemanticAttributes.HTTP_URL, requestUrl),
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                requestUrl.replace("localhost", "0.0.0.0")))));
  }
}
