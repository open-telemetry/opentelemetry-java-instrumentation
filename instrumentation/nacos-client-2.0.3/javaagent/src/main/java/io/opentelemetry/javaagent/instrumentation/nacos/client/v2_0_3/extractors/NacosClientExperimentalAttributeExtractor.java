package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.extractors;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.NacosClientRequest;
import javax.annotation.Nullable;

public class NacosClientExperimentalAttributeExtractor
    implements AttributesExtractor<NacosClientRequest, Response> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      NacosClientRequest nacosClientRequest) {
    attributes.putAll(nacosClientRequest.getAttributes());
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      NacosClientRequest nacosClientRequest, @Nullable Response response,
      @Nullable Throwable error) {

  }
}
