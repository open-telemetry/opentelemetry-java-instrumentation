/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;

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
public abstract class AbstractQueryProtocolModelTest {
  private final MockWebServerExtension server = new MockWebServerExtension();

  @BeforeAll
  public void setup() {
    server.start();
  }

  @AfterAll
  public void end() {
    server.stop();
  }

  @BeforeEach
  public void setupEach() {
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
                    span.hasAttributesSatisfying(
                        attributes -> {
                          assertThat(attributes)
                              .hasEntrySatisfying(
                                  HTTP_URL,
                                  entry -> {
                                    assertThat(entry)
                                        .satisfies(
                                            value -> {
                                              URI uri = URI.create(value);
                                              assertThat(uri.getQuery()).isNull();
                                            });
                                  });
                        });
                  });
            });
  }
}
