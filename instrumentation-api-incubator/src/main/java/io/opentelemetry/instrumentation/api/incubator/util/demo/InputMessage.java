package io.opentelemetry.instrumentation.api.incubator.util.demo;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.incubator.util.JsonWriter;
import java.util.List;

public class InputMessage {

  private final String role;

  private final List<MessagePart> parts;

  private final String name;

  @CanIgnoreReturnValue
  public JsonWriter write(JsonWriter writer) {
    writer = writer.beginObject()
        .name("role")
        .value(role);

    writer = writer.name("parts")
        .beginArray();
    for (MessagePart part : parts) {
      writer = part.write(writer);
    }
    writer = writer.endArray();

    return writer.name("name")
        .value(name)
        .endObject();
  }

  public InputMessage(String role, List<MessagePart> parts, String name) {
    this.role = role;
    this.parts = parts;
    this.name = name;
  }

  public interface MessagePart {
    JsonWriter write(JsonWriter writer);
  }

  public static final class TextPart implements MessagePart {
    private final String text;

    @Override
    @CanIgnoreReturnValue
    public JsonWriter write(JsonWriter writer) {
      return writer.beginObject()
          .name("type")
          .value("text")
          .name("text")
          .value(text).endObject();
    }

    public TextPart(String text) {
      this.text = text;
    }
  }
}
