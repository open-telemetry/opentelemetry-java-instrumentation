/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.aws;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertNoCamelMessagingMetrics;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sns.AmazonSNSAsyncClient;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SnsProducerCamelTest {

  private static final String topicName = "snsCamelTest";
  private static final String topicArn = "arn:aws:sns:us-east-1:123456789012:" + topicName;
  private static final AtomicReference<String> publishRequest = new AtomicReference<>();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static HttpServer server;
  private static AmazonSNSAsyncClient snsClient;

  @BeforeAll
  static void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", PortUtils.findOpenPort()), 0);
    server.createContext("/", SnsProducerCamelTest::handleRequest);
    server.start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpoint =
        new AwsClientBuilder.EndpointConfiguration(
            "http://localhost:" + server.getAddress().getPort(), "us-east-1");
    snsClient =
        (AmazonSNSAsyncClient)
            AmazonSNSAsyncClient.asyncBuilder()
                .withCredentials(credentials)
                .withEndpointConfiguration(endpoint)
                .build();
  }

  @AfterAll
  static void cleanUp() {
    snsClient.shutdown();
    server.stop(0);
  }

  @Test
  void camelSnsSendAllowsNestedAwsSpanAndPropagatesContext() {
    AwsConnector awsConnector = new AwsConnector(null, null, snsClient, null);
    CamelSpringApplication camelApp =
        new CamelSpringApplication(
            awsConnector, SnsProducerConfig.class, ImmutableMap.of("topicName", topicName));
    camelApp.start();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> AwsSpanAssertions.sns(span, "SNS.ListTopics", null, null).hasNoParent()));
    testing.clearData();

    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}");

    AtomicReference<SpanData> camelSend = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> CamelSpanAssertions.direct(span, "input"),
                span -> {
                  camelSend.set(trace.getSpan(1));
                  CamelSpanAssertions.snsPublish(span, topicName).hasParent(trace.getSpan(0));
                  if (emitStableMessagingSemconv()) {
                    span.hasAttribute(MESSAGING_MESSAGE_ID, "message-id");
                  }
                },
                span -> {
                  AwsSpanAssertions.sns(span, "SNS.Publish", topicArn, topicArn)
                      .hasParent(trace.getSpan(1));
                }));

    assertThat(publishRequest.get())
        .contains("Name=traceparent")
        .contains("-" + camelSend.get().getSpanId() + "-");
    assertNoCamelMessagingMetrics(testing);
    camelApp.stop();
  }

  private static void handleRequest(HttpExchange exchange) throws IOException {
    String requestBody = new String(ByteStreams.toByteArray(exchange.getRequestBody()), UTF_8);
    String decodedRequest = URLDecoder.decode(requestBody, UTF_8.name());
    String response;
    if (decodedRequest.contains("Action=ListTopics")) {
      response =
          "<ListTopicsResponse xmlns=\"https://sns.amazonaws.com/doc/2010-03-31/\">"
              + "<ListTopicsResult><Topics><member><TopicArn>"
              + topicArn
              + "</TopicArn></member></Topics></ListTopicsResult>"
              + "<ResponseMetadata><RequestId>request-id</RequestId></ResponseMetadata>"
              + "</ListTopicsResponse>";
    } else {
      publishRequest.set(requestBody);
      response =
          "<PublishResponse xmlns=\"https://sns.amazonaws.com/doc/2010-03-31/\">"
              + "<PublishResult><MessageId>message-id</MessageId></PublishResult>"
              + "<ResponseMetadata><RequestId>request-id</RequestId></ResponseMetadata>"
              + "</PublishResponse>";
    }
    byte[] responseBytes = response.getBytes(UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/xml");
    exchange.sendResponseHeaders(200, responseBytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(responseBytes);
    }
  }
}
