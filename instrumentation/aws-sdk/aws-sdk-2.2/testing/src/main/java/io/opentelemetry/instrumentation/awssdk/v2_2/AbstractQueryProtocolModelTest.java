/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractQueryProtocolModelTest {
  private final MockWebServerExtension server = new MockWebServerExtension();

  @BeforeAll
  void setup() {
    server.start();
  }

  @AfterAll
  void end() {
    server.stop();
  }

  @BeforeEach
  void setupEach() {
    server.beforeTestExecution(null);
  }

  protected abstract ClientOverrideConfiguration.Builder createClientOverrideConfigurationBuilder();

  protected abstract InstrumentationExtension getTesting();

  @Test
  void testClientWithQueryProtocolModel() {
    server.enqueue(
        HttpResponse.of(
            HttpStatus.OK,
            MediaType.PLAIN_TEXT_UTF_8,
            "<SendEmailResponse><MessageId>12345</MessageId></SendEmailResponse>"));
    SesClient ses =
        SesClient.builder()
            .endpointOverride(server.httpUri())
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("foo", "bar")))
            .overrideConfiguration(createClientOverrideConfigurationBuilder().build())
            .region(Region.US_WEST_2)
            .build();

    Destination destination = Destination.builder().toAddresses("dest@test.com").build();
    Content content = Content.builder().data("content").build();
    Content sub = Content.builder().data("subject").build();
    Body body = Body.builder().html(content).build();
    Message msg = Message.builder().subject(sub).body(body).build();
    SendEmailRequest emailRequest =
        SendEmailRequest.builder()
            .destination(destination)
            .message(msg)
            .source("source@test.com")
            .build();

    ses.sendEmail(emailRequest);

    getTesting()
        .waitAndAssertTraces(
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    span.hasKind(SpanKind.CLIENT);
                    span.hasAttributesSatisfyingExactly(
                        equalTo(SERVER_ADDRESS, "127.0.0.1"),
                        equalTo(SERVER_PORT, server.httpPort()),
                        equalTo(HTTP_REQUEST_METHOD, "POST"),
                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                        equalTo(RPC_SYSTEM, "aws-api"),
                        equalTo(RPC_SERVICE, "Ses"),
                        equalTo(RPC_METHOD, "SendEmail"),
                        equalTo(AWS_REQUEST_ID, "UNKNOWN"),
                        equalTo(stringKey("aws.agent"), isJavaagentTest() ? "java-aws-sdk" : null),
                        satisfies(
                            URL_FULL,
                            val ->
                                val.satisfies(
                                    value -> {
                                      URI uri = URI.create(value);
                                      assertThat(uri.getQuery()).isNull();
                                    })));
                  });
            });
  }

  private boolean isJavaagentTest() {
    return getClass().getName().startsWith("io.opentelemetry.javaagent.");
  }
}
