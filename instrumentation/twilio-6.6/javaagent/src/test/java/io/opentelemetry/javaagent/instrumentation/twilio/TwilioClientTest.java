/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.http.NetworkHttpClient;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwilioClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String ACCOUNT_SID = "abc";
  private static final String AUTH_TOKEN = "efg";

  private static final String MESSAGE_RESPONSE_BODY =
      " {\n"
          + "      \"account_sid\": \"AC14984e09e497506cf0d5eb59b1f6ace7\",\n"
          + "      \"api_version\": \"2010-04-01\",\n"
          + "      \"body\": \"Hello, World!\",\n"
          + "      \"date_created\": \"Thu, 30 Jul 2015 20:12:31 +0000\",\n"
          + "      \"date_sent\": \"Thu, 30 Jul 2015 20:12:33 +0000\",\n"
          + "      \"date_updated\": \"Thu, 30 Jul 2015 20:12:33 +0000\",\n"
          + "      \"direction\": \"outbound-api\",\n"
          + "      \"from\": \"+14155552345\",\n"
          + "      \"messaging_service_sid\": \"MGXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\",\n"
          + "      \"num_media\": \"0\",\n"
          + "      \"num_segments\": \"1\",\n"
          + "      \"price\": -0.00750,\n"
          + "      \"price_unit\": \"USD\",\n"
          + "      \"sid\": \"MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\",\n"
          + "      \"status\": \"sent\",\n"
          + "      \"subresource_uris\": {\n"
          + "        \"media\": \"/2010-04-01/Accounts/AC14984e09e497506cf0d5eb59b1f6ace7/Messages/SMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Media.json\"\n"
          + "      },\n"
          + "      \"to\": \"+14155552345\",\n"
          + "      \"uri\": \"/2010-04-01/Accounts/AC14984e09e497506cf0d5eb59b1f6ace7/Messages/SMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.json\"\n"
          + "    }";
  private static final String CALL_RESPONSE_BODY =
      " {\n"
          + "      \"account_sid\": \"ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\",\n"
          + "      \"annotation\": null,\n"
          + "      \"answered_by\": null,\n"
          + "      \"api_version\": \"2010-04-01\",\n"
          + "      \"caller_name\": null,\n"
          + "      \"date_created\": \"Tue, 31 Aug 2010 20:36:28 +0000\",\n"
          + "      \"date_updated\": \"Tue, 31 Aug 2010 20:36:44 +0000\",\n"
          + "      \"direction\": \"inbound\",\n"
          + "      \"duration\": \"15\",\n"
          + "      \"end_time\": \"Tue, 31 Aug 2010 20:36:44 +0000\",\n"
          + "      \"forwarded_from\": \"+141586753093\",\n"
          + "      \"from\": \"+15017122661\",\n"
          + "      \"from_formatted\": \"(501) 712-2661\",\n"
          + "      \"group_sid\": null,\n"
          + "      \"parent_call_sid\": null,\n"
          + "      \"phone_number_sid\": \"PNXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\",\n"
          + "      \"price\": -0.03000,\n"
          + "      \"price_unit\": \"USD\",\n"
          + "      \"sid\": \"CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\",\n"
          + "      \"start_time\": \"Tue, 31 Aug 2010 20:36:29 +0000\",\n"
          + "      \"status\": \"completed\",\n"
          + "      \"subresource_uris\": {\n"
          + "        \"notifications\": \"/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Notifications.json\",\n"
          + "        \"recordings\": \"/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Recordings.json\",\n"
          + "        \"feedback\": \"/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Feedback.json\",\n"
          + "        \"feedback_summaries\": \"/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/FeedbackSummary.json\"\n"
          + "      },\n"
          + "      \"to\": \"+15558675310\",\n"
          + "      \"to_formatted\": \"(555) 867-5310\",\n"
          + "      \"uri\": \"/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.json\"\n"
          + "    }";
  private static final String ERROR_RESPONSE_BODY =
      "{\n"
          + "      \"code\": 123,\n"
          + "      \"message\": \"Testing Failure\",\n"
          + "      \"code\": 567,\n"
          + "      \"more_info\": \"Testing\"\n"
          + "    }";

  @Mock private TwilioRestClient twilioRestClient;

  @Mock private CloseableHttpClient httpClient;

  @BeforeAll
  static void setUp() {
    Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
  }

  @AfterAll
  static void tearDown() {
    Twilio.getExecutorService().shutdown();
    Twilio.setExecutorService(null);
    Twilio.setRestClient(null);
  }

  @Test
  void synchronousMessage() {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                200));

    Message message =
        testing.runWithSpan(
            "test",
            () ->
                Message.creator(
                        new PhoneNumber("+1 555 720 5913"),
                        new PhoneNumber("+1 555 555 5215"),
                        "Hello world!")
                    .create(twilioRestClient));

    assertThat(message.getBody()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("MessageCreator.create")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @Test
  void synchronousCall() throws URISyntaxException {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(CALL_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                200));

    Call call =
        testing.runWithSpan(
            "test",
            () ->
                Call.creator(
                        new PhoneNumber("+15558881234"),
                        new PhoneNumber("+15559994321"),
                        new URI("http://twimlets.com/holdmusic?Bucket=com.twilio.music.ambient"))
                    .create(twilioRestClient));

    assertThat(call.getStatus()).isEqualTo(Call.Status.COMPLETED);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("CallCreator.create")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"), "com.twilio.rest.api.v2010.account.Call"),
                            equalTo(
                                stringKey("twilio.account"), "ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.sid"), "CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "completed"))));
  }

  @Test
  void httpClient() throws IOException {
    CloseableHttpResponse response = mockResponse(MESSAGE_RESPONSE_BODY, 200);
    when(httpClient.execute(any())).thenReturn(response);

    HttpClientBuilder clientBuilder = getHttpClientBuilder(httpClient);

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder);

    TwilioRestClient realTwilioRestClient =
        new TwilioRestClient.Builder("username", "password")
            .accountSid(ACCOUNT_SID)
            .httpClient(networkHttpClient)
            .build();

    Message message =
        testing.runWithSpan(
            "test",
            () ->
                Message.creator(
                        new PhoneNumber("+1 555 720 5913"),
                        new PhoneNumber("+1 555 555 5215"),
                        "Hello world!")
                    .create(realTwilioRestClient));

    assertThat(message.getBody()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("MessageCreator.create")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @SuppressWarnings("CannotMockMethod")
  private static @NotNull HttpClientBuilder getHttpClientBuilder(CloseableHttpClient httpClient) {
    HttpClientBuilder clientBuilder = spy(HttpClientBuilder.create());
    when(clientBuilder.build()).thenReturn(httpClient);
    return clientBuilder;
  }

  @Test
  void httpClientRetry() throws IOException {
    CloseableHttpResponse response1 = mockResponse(ERROR_RESPONSE_BODY, 500);
    CloseableHttpResponse response2 = mockResponse(MESSAGE_RESPONSE_BODY, 200);
    when(httpClient.execute(any())).thenReturn(response1, response2);

    HttpClientBuilder clientBuilder = getHttpClientBuilder(httpClient);

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder);

    TwilioRestClient realTwilioRestClient =
        new TwilioRestClient.Builder("username", "password")
            .accountSid(ACCOUNT_SID)
            .httpClient(networkHttpClient)
            .build();

    Message message =
        testing.runWithSpan(
            "test",
            () ->
                Message.creator(
                        new PhoneNumber("+1 555 720 5913"),
                        new PhoneNumber("+1 555 555 5215"),
                        "Hello world!")
                    .create(realTwilioRestClient));

    assertThat(message.getBody()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("MessageCreator.create")
                        .hasParent(trace.getSpan(0))
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @Test
  void httpClientRetryAsync() throws Exception {
    CloseableHttpResponse response1 = mockResponse(ERROR_RESPONSE_BODY, 500);
    CloseableHttpResponse response2 = mockResponse(MESSAGE_RESPONSE_BODY, 200);
    when(httpClient.execute(any())).thenReturn(response1, response2);

    HttpClientBuilder clientBuilder = getHttpClientBuilder(httpClient);

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder);

    TwilioRestClient realTwilioRestClient =
        new TwilioRestClient.Builder("username", "password")
            .accountSid(ACCOUNT_SID)
            .httpClient(networkHttpClient)
            .build();

    Message message =
        testing.runWithSpan(
            "test",
            () -> {
              ListenableFuture<Message> future =
                  Message.creator(
                          new PhoneNumber("+1 555 720 5913"),
                          new PhoneNumber("+1 555 555 5215"),
                          "Hello world!")
                      .createAsync(realTwilioRestClient);

              try {
                return future.get(10, TimeUnit.SECONDS);
              } finally {
                Thread.sleep(1000);
              }
            });

    assertThat(message.getBody()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("MessageCreator.createAsync")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @Test
  void syncFailure() {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                500));

    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "test",
                    () ->
                        Message.creator(
                                new PhoneNumber("+1 555 720 5913"),
                                new PhoneNumber("+1 555 555 5215"),
                                "Hello world!")
                            .create(twilioRestClient)))
        .isInstanceOf(ApiException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(new ApiException("Testing Failure")),
                span ->
                    span.hasName("MessageCreator.create")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new ApiException("Testing Failure"))));
  }

  @Test
  void rootSpan() {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                200));

    Message message =
        Message.creator(
                new PhoneNumber("+1 555 720 5913"),
                new PhoneNumber("+1 555 555 5215"),
                "Hello world!")
            .create(twilioRestClient);

    assertThat(message.getBody()).isEqualTo("Hello, World!");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("MessageCreator.create")
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @Test
  void asynchronousCall() throws Exception {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                200));

    Message message =
        testing.runWithSpan(
            "test",
            () -> {
              ListenableFuture<Message> future =
                  Message.creator(
                          new PhoneNumber("+1 555 720 5913"),
                          new PhoneNumber("+1 555 555 5215"),
                          "Hello world!")
                      .createAsync(twilioRestClient);

              try {
                return future.get(10, TimeUnit.SECONDS);
              } finally {
                Thread.sleep(1000);
              }
            });

    assertThat(message).isNotNull();
    assertThat(message.getBody()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("MessageCreator.createAsync")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("twilio.type"),
                                "com.twilio.rest.api.v2010.account.Message"),
                            equalTo(
                                stringKey("twilio.account"), "AC14984e09e497506cf0d5eb59b1f6ace7"),
                            equalTo(stringKey("twilio.sid"), "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                            equalTo(stringKey("twilio.status"), "sent"))));
  }

  @Test
  void asynchronousError() {
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(
                new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8)),
                500));

    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "test",
                    () -> {
                      ListenableFuture<Message> future =
                          Message.creator(
                                  new PhoneNumber("+1 555 720 5913"),
                                  new PhoneNumber("+1 555 555 5215"),
                                  "Hello world!")
                              .createAsync(twilioRestClient);

                      try {
                        return future.get(10, TimeUnit.SECONDS);
                      } finally {
                        Thread.sleep(1000);
                      }
                    }))
        .isInstanceOf(ExecutionException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(new ApiException("Testing Failure")),
                span ->
                    span.hasName("MessageCreator.createAsync")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new ApiException("Testing Failure"))));
  }

  private static CloseableHttpResponse mockResponse(String body, int statusCode)
      throws IOException {
    HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent())
        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(httpEntity.isRepeatable()).thenReturn(true);

    StatusLine statusLine = mock(StatusLine.class);
    when(statusLine.getStatusCode()).thenReturn(statusCode);

    CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
    when(httpResponse.getEntity()).thenReturn(httpEntity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);

    return httpResponse;
  }
}
