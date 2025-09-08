package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

public enum Role {
  SYSTEM("system"),
  USER("user"),
  ASSISTANT("assistant"),
  TOOL("tool"),
  DEVELOPER("developer");

  private final String value;

  public String getValue() {
    return value;
  }

  Role(String value) {
    this.value = value;
  }
}
