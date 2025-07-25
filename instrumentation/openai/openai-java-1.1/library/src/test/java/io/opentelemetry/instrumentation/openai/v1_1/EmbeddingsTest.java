package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.singletonList;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;

class EmbeddingsTest extends AbstractEmbeddingsTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private static OpenAITelemetry telemetry;

  @BeforeAll
  static void setup() {
    telemetry =
        OpenAITelemetry.builder(testing.getOpenTelemetry()).setCaptureMessageContent(true).build();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenAIClient wrap(OpenAIClient client) {
    return telemetry.wrap(client);
  }

  @Override
  protected OpenAIClientAsync wrap(OpenAIClientAsync client) {
    return telemetry.wrap(client);
  }

  @Override
  protected List<Consumer<SpanDataAssert>> maybeWithTransportSpan(Consumer<SpanDataAssert> span) {
    return singletonList(span);
  }
}
