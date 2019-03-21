package datadog.trace.instrumentation.twilio;

import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import java.lang.reflect.Method;

/** Decorate Twilio span's with relevant contextual information. */
public class TwilioClientDecorator extends ClientDecorator {

  public static final TwilioClientDecorator DECORATE = new TwilioClientDecorator();

  static final String COMPONENT_NAME = "twilio-sdk";

  @Override
  protected String spanType() {
    return DDSpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {COMPONENT_NAME};
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
  }

  @Override
  protected String service() {
    return COMPONENT_NAME;
  }

  /** Decorate trace based on service execution metadata */
  public Span onServiceExecution(
      final Span span, final Object serviceExecutor, final String methodName) {

    // Drop common package prefix (com.twilio.rest)
    final String simpleClassName =
        serviceExecutor.getClass().getCanonicalName().replaceFirst("^com\\.twilio\\.rest\\.", "");

    span.setTag(DDTags.RESOURCE_NAME, String.format("%s.%s", simpleClassName, methodName));

    return span;
  }

  /** Annotate the span with the results of the operation. */
  public Span onResult(final Span span, final Object result) {

    // Provide helpful metadata for some of the more common response types
    span.setTag("twilio.type", result.getClass().getCanonicalName());
    // Instrument the most popular resource types directly
    if (result instanceof Message) {
      final Message message = (Message) result;
      span.setTag("twilio.type", result.getClass().getCanonicalName());
      span.setTag("twilio.account", message.getAccountSid());
      span.setTag("twilio.sid", message.getSid());
      span.setTag("twilio.status", message.getStatus().toString());
    } else if (result instanceof Call) {
      final Call call = (Call) result;
      span.setTag("twilio.account", call.getAccountSid());
      span.setTag("twilio.sid", call.getSid());
      span.setTag("twilio.parentSid", call.getParentCallSid());
      span.setTag("twilio.status", call.getStatus().toString());
    } else {
      // Use reflection to gather insight from other types; note that Twilio requests take close to
      // 1 second, so the added hit from reflection here is relatively minimal in the grand scheme
      // of things
      setTagIfPresent(span, result, "twilio.sid", "getSid");
      setTagIfPresent(span, result, "twilio.account", "getAccountSid");
      setTagIfPresent(span, result, "twilio.status", "getStatus");
    }

    return span;
  }

  private void setTagIfPresent(
      final Span span, final Object result, final String tag, final String getter) {
    try {
      final Method method = result.getClass().getMethod(getter);
      final Object value = method.invoke(result);

      if (value != null) {
        span.setTag(tag, value.toString());
      }

    } catch (final Exception e) {
      // Expected that this won't work for all result types
    }
  }
}
