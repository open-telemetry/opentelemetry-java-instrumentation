package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import io.opentelemetry.instrumentation.spring.ai.v1_0.AbstractChatClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ChatClientTest extends AbstractChatClientTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}
