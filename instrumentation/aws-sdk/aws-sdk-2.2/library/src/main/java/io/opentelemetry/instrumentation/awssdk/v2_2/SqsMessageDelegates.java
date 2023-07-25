package io.opentelemetry.instrumentation.awssdk.v2_2;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.sqs.model.Message;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SqsMessageDelegates {
  private static final Map<Class<?>, SqsMessageDelegate<?>> messageDelegates = new HashMap<>();

  public static void register(Class<?> clazz, SqsMessageDelegate<?> messageDelegate) {
    messageDelegates.put(clazz, messageDelegate);
  }

  static {
    register(Message.class, new SqsMessageDelegate<Message>() {
      @Override
      public int getPayloadSize(Message message) {
        return message.body().length();
      }

      @Override
      public SpanContext getUpstreamContext(OpenTelemetry openTelemetry, Message message) {
        TextMapPropagator messagingPropagator = openTelemetry.getPropagators()
            .getTextMapPropagator();

        Map<String, SdkPojo> messageAtributes = SqsMessageAccess.getMessageAttributes(message);

        Context context =
            SqsParentContext.ofMessageAttributes(messageAtributes, messagingPropagator);

        if (context == Context.root()) {
          context = SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));
        }

        return Span.fromContext(context).getSpanContext();
      }
    });

    register(SQSEvent.SQSMessage.class,
        new SqsMessageDelegate<SQSEvent.SQSMessage>() {
          private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";
          static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

          @Override
          public int getPayloadSize(SQSEvent.SQSMessage message) {
            return message.getBody().length();
          }

          @Override
          public SpanContext getUpstreamContext(OpenTelemetry openTelemetry, SQSEvent.SQSMessage message) {
            String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);

            if (parentHeader != null) {
              Context xrayContext =
                  AwsXrayPropagator.getInstance()
                      .extract(
                          Context.root(),
                          Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                          MapGetter.INSTANCE);
              return Span.fromContext(xrayContext).getSpanContext();
            }

            return null;
          }
        });
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
    return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }

  private SqsMessageDelegates() {
  }

  @SuppressWarnings("unchecked")
  public static <T> SqsMessageDelegate<T> get(T message) {
    return (SqsMessageDelegate<T>) messageDelegates.get(message.getClass());
  }
}
