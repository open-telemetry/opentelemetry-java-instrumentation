# Settings for the Spring AI instrumentation

| System property                                      | Type    | Default | Description                                                                 |
| ---------------------------------------------------- | ------- | ------- | --------------------------------------------------------------------------- |
| `otel.instrumentation.genai.capture-message-content` | Boolean | `false` | Record content of system, user, assistant, and tool messages in log events. |

This instrumentation creates GenAI client spans for `ChatModel.call` and `ChatModel.stream`.
It is provider-neutral and supports Spring AI 1.x model implementations.
