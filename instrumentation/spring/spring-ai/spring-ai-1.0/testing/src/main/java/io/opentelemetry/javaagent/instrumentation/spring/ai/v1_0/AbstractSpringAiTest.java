package io.opentelemetry.instrumentation.spring.ai.v1_0;

import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.recording.RecordingExtension;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public abstract class AbstractSpringAiTest {

  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-1.0";

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

  protected final ToolCallingManager getToolCallingManager() {
    return ToolCallingManager.builder()
        .toolCallbackResolver(
            new StaticToolCallbackResolver(getToolCallbacks()))
        .build();
  }

  protected final OpenAiChatModel getChatModel() {
    if (chatModel == null) {
      chatModel = OpenAiChatModel.builder()
          .openAiApi(getOpenAiApi())
          .toolCallingManager(getToolCallingManager())
          .build();
    }
    return chatModel;
  }

  protected final ChatClient getChatClient() {
    return ChatClient.builder(getChatModel()).build();
  }

  protected final List<ToolCallback> getToolCallbacks() {
    return singletonList(
        FunctionToolCallback.builder("get_weather", new GetWeatherFunction())
            .description("The location to get the current temperature for")
            .inputType(ToolInput.class)
            .build());
  }

  @JsonClassDescription("The location to get the current temperature for")
  public static class ToolInput {
    @JsonPropertyDescription("location")
    private String location;

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }

    @JsonCreator
    public ToolInput(@JsonProperty("location") String location) {
      this.location = location;
    }
  }

  private static class GetWeatherFunction implements Function<ToolInput, String> {
    @Override
    public String apply(ToolInput location) {
      if (location.getLocation().contains("London")) {
        return "15 degrees and raining";
      }
      return "25 degrees and sunny";
    }
  }

}
