package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.pubsub.publisher.PubsubPublisherInstrumentation;
import io.opentelemetry.javaagent.instrumentation.pubsub.subscriber.PubsubAckReplyInstrumentation;
import io.opentelemetry.javaagent.instrumentation.pubsub.subscriber.PubsubSubscriberInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.List;

import static java.util.Arrays.asList;

@AutoService(InstrumentationModule.class)
public class PubsubInstrumentationModule extends InstrumentationModule {
  public PubsubInstrumentationModule() {
    super("pubsub", "pubsub-1");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.kognic.otel.instrumentation.pubsub");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
            new PubsubPublisherInstrumentation(),
            new PubsubAckReplyInstrumentation(),
            new PubsubSubscriberInstrumentation());
  }
}
