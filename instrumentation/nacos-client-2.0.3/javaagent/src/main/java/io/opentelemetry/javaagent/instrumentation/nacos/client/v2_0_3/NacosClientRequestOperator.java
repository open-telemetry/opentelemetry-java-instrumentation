package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3;

import com.alibaba.nacos.api.remote.request.Request;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class NacosClientRequestOperator {
  private final Function<Request, String> spanNameHandler;
  private final BiConsumer<AttributesBuilder, Request> attributesHandler;

  public NacosClientRequestOperator(
      Function<Request, String> spanNameHandler,
      BiConsumer<AttributesBuilder, Request> attributesHandler) {
    this.spanNameHandler = spanNameHandler;
    this.attributesHandler = attributesHandler;
  }

  public String getName(Request request) {
    return spanNameHandler == null ? "<unknown>"
        : NacosClientConstants.NACOS_PREFIX + spanNameHandler.apply(request);
  }

  public Attributes getAttributes(Request request) {
    AttributesBuilder builder = Attributes.builder();
    if (attributesHandler != null) {
      attributesHandler.accept(builder, request);
    }
    return builder.build();
  }

}
