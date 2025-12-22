# Settings for the OpenAI instrumentation

| System property                                      | Type    | Default | Description                                                            |
|------------------------------------------------------|---------|---------|------------------------------------------------------------------------|
| `otel.instrumentation.genai.capture-message-content` | Boolean | `false` | Record content of user and LLM messages.                               |
| `otel.semconv-stability.opt-in`                      | Boolean | ``      | Enable experimental features when set as `gen_ai_latest_experimental`. |
