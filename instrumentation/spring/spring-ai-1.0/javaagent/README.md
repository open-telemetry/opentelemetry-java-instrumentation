# Settings for the Spring AI instrumentation

| System property                                                                                  | Type    | Default | Description                                                                                              |
| ------------------------------------------------------------------------------------------------ | ------- | ------- | -------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.genai.capture-message-content`                                             | Boolean | `false` | Record content of user and LLM messages in log events.                                                   |
| `otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled` | Boolean | `false` | Record content in the experimental `gen_ai.input.messages` and `gen_ai.output.messages` span attributes. |
| `otel.instrumentation.spring-ai.experimental.message-content-span-attribute.max-length`          | Integer | `8192`  | Maximum number of characters captured for each message content in a span attribute.                      |

This instrumentation creates GenAI client spans for `ChatModel.call` and `ChatModel.stream`.
It is provider-neutral and supports Spring AI 1.x model implementations.
