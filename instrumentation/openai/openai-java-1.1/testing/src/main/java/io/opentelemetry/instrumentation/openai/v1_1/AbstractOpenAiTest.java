/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.recording.RecordingExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(AbstractOpenAiTest.TestType.class)
abstract class AbstractOpenAiTest {
  enum TestType {
    SYNC,
    SYNC_FROM_ASYNC,
    ASYNC,
    ASYNC_FROM_SYNC,
  }

  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.openai-java-1.1";

  private static final String API_URL = "https://api.openai.com/v1";

  @RegisterExtension static final RecordingExtension recording = new RecordingExtension(API_URL);

  protected abstract InstrumentationExtension getTesting();

  protected abstract OpenAIClient wrap(OpenAIClient client);

  protected abstract OpenAIClientAsync wrap(OpenAIClientAsync client);

  protected final OpenAIClient getRawClient() {
    OpenAIOkHttpClient.Builder builder =
        OpenAIOkHttpClient.builder().baseUrl("http://localhost:" + recording.getPort());
    if (recording.isRecording()) {
      builder.apiKey(System.getenv("OPENAI_API_KEY"));
    } else {
      builder.apiKey("unused");
    }
    return builder.build();
  }

  protected final OpenAIClientAsync getRawClientAsync() {
    OpenAIOkHttpClientAsync.Builder builder =
        OpenAIOkHttpClientAsync.builder().baseUrl("http://localhost:" + recording.getPort());
    if (recording.isRecording()) {
      builder.apiKey(System.getenv("OPENAI_API_KEY"));
    } else {
      builder.apiKey("unused");
    }
    return builder.build();
  }

  protected final OpenAIClient getClient() {
    return wrap(getRawClient());
  }

  protected final OpenAIClientAsync getClientAsync() {
    return wrap(getRawClientAsync());
  }

  protected abstract List<Consumer<SpanDataAssert>> maybeWithTransportSpan(
      Consumer<SpanDataAssert> span);

  @Parameter protected TestType testType;
}
