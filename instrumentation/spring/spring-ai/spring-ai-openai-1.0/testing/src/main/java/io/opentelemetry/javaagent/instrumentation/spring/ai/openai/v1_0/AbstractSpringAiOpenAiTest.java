package io.opentelemetry.instrumentation.spring.ai.openai.v1_0;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.recording.RecordingExtension;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public abstract class AbstractSpringAiOpenaiTest {

  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-openai-1.0";

  private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode";

  @RegisterExtension
  static final RecordingExtension recording = new RecordingExtension(API_URL);

  protected abstract InstrumentationExtension getTesting();

  private OpenAiApi openAiApi;

  private OpenAiChatModel chatModel;

  protected final OpenAiApi getOpenAiApi() {
    if (openAiApi == null) {
      HttpClient httpClient = HttpClient.newBuilder()
          .version(Version.HTTP_1_1)
          .build();

      OpenAiApi.Builder builder = OpenAiApi.builder()
          .restClientBuilder(RestClient.builder()
              .requestFactory(new JdkClientHttpRequestFactory(httpClient)))
          .webClientBuilder(WebClient.builder()
              .clientConnector(new JdkClientHttpConnector(httpClient)))
          .baseUrl("http://localhost:" + recording.getPort());
      if (recording.isRecording()) {
        builder.apiKey(System.getenv("OPENAI_API_KEY"));
      } else {
        builder.apiKey("unused");
      }
      openAiApi = builder.build();
    }
    return openAiApi;
  }

  protected final OpenAiChatModel getChatModel() {
    if (chatModel == null) {
      chatModel = OpenAiChatModel.builder()
          .openAiApi(getOpenAiApi())
          .toolExecutionEligibilityPredicate((o1, o2) -> false)
          .build();
    }
    return chatModel;
  }
}
