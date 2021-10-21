package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import org.apache.rocketmq.client.hook.SendMessageContext;

class RocketMqInstrumenterFactory {

  static Instrumenter<SendMessageContext, SendMessageContext> createProducerInstrumenter(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes) {

    InstrumenterBuilder<SendMessageContext, SendMessageContext> instrumenterBuilder = Instrumenter
        .<SendMessageContext, SendMessageContext>newBuilder(openTelemetry,
            "io.opentelemetry.rocketmq-client-4.8", RocketMqInstrumenterFactory::spanNameOnProduce)
        .addAttributesExtractor(new RockerMqProducerAttributeExtractor());
    if (captureExperimentalSpanAttributes) {
      instrumenterBuilder
          .addAttributesExtractor(new RockerMqProducerExperimentalAttributeExtractor());
    }
    return instrumenterBuilder.newProducerInstrumenter(TextMapInjectAdapter.SETTER);
  }

  private static String spanNameOnProduce(SendMessageContext request) {
    return request.getMessage().getTopic() + " send";
  }
}
