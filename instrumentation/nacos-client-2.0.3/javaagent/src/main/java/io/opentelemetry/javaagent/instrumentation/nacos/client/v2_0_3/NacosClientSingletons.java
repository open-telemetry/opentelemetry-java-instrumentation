package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors.NacosClientCodeAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors.NacosClientExperimentalAttributeExtractor;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors.NacosClientSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors.NacosClientSpanStatusExtractor;

public final class NacosClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nacos-client-2.0.3";
  private static final Instrumenter<NacosClientRequest, Response> INSTRUMENTER = create();

  public static Instrumenter<NacosClientRequest, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private static Instrumenter<NacosClientRequest, Response> create() {
    CodeAttributesGetter<NacosClientRequest> codeAttributesGetter = new NacosClientCodeAttributesGetter();
    SpanNameExtractor<NacosClientRequest> spanNameExtractor = new NacosClientSpanNameExtractor();
    SpanStatusExtractor<NacosClientRequest, Response> spanStatusExtractor = new NacosClientSpanStatusExtractor();
    InstrumenterBuilder<NacosClientRequest, Response> builder =
        Instrumenter.<NacosClientRequest, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setSpanStatusExtractor(spanStatusExtractor);
    builder.addAttributesExtractor(
        AttributesExtractor.constant(AttributeKey.stringKey("service.discovery.system"), "nacos")
    );
    builder.addAttributesExtractor(new NacosClientExperimentalAttributeExtractor());
    return builder.buildInstrumenter();
  }

  private NacosClientSingletons() {}
}
