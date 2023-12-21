package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.Function;
import javax.annotation.Nullable;

public class SnsAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    setRequestAttribute(attributes, SemanticAttributes.MESSAGING_DESTINATION_NAME,
        request.getOriginalRequest(), RequestAccess::getTopicArn);
  }

  private static void setRequestAttribute(
      AttributesBuilder attributes,
      AttributeKey<String> key,
      Object request,
      Function<Object, String> getter) {
    String value = getter.apply(request);
    if (value != null) {
      attributes.put(key, value);
    }
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context, Request<?> request,
      @Nullable Response<?> response, @Nullable Throwable error) {}
}
