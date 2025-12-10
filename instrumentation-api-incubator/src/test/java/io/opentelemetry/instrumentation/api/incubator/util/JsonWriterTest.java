package io.opentelemetry.instrumentation.api.incubator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.instrumentation.api.incubator.util.demo.InputMessage;
import io.opentelemetry.instrumentation.api.incubator.util.demo.InputMessage.TextPart;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

  @Test
  void testJsonWriter() {
    InputMessage inputMessage = new InputMessage("user",
        Collections.singletonList(new TextPart("Hello?")), "Bob");

    try (JsonWriter writer = new JsonWriter()) {
      String message = inputMessage.write(writer).toString();
      assertEquals("{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"Hello?\"}],\"name\":\"Bob\"}", message);
    }
  }
}
