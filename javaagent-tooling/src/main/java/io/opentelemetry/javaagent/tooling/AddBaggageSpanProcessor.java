package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;

public class AddBaggageSpanProcessor implements OnStartSpanProcessor {
  @Override
  public void onStart(Context context, ReadWriteSpan span) {
    Baggage baggage = Baggage.fromContext(context);
    baggage.forEach(
        (key, value) ->
            span.setAttribute(
                // add prefix to key to not override existing attributes
                "baggage." + key,
                value.getValue()));
  }
}
