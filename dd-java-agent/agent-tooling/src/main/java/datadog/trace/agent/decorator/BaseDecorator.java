package datadog.trace.agent.decorator;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

public abstract class BaseDecorator {

  protected final boolean traceAnalyticsEnabled;
  protected final float traceAnalyticsSampleRate;

  protected BaseDecorator() {
    traceAnalyticsEnabled =
        Config.traceAnalyticsIntegrationEnabled(
            new TreeSet<>(Arrays.asList(instrumentationNames())), traceAnalyticsDefault());
    float rate = 1.0f;
    for (final String name : instrumentationNames()) {
      rate =
          Config.getFloatSettingFromEnvironment(
              "integration." + name + ".analytics.sample-rate", rate);
    }
    traceAnalyticsSampleRate = rate;
  }

  protected abstract String[] instrumentationNames();

  protected abstract String spanType();

  protected abstract String component();

  protected boolean traceAnalyticsDefault() {
    return false;
  }

  public Span afterStart(final Span span) {
    assert span != null;
    span.setTag(DDTags.SPAN_TYPE, spanType());
    Tags.COMPONENT.set(span, component());
    if (traceAnalyticsEnabled) {
      span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, traceAnalyticsSampleRate);
    }
    return span;
  }

  public Span beforeFinish(final Span span) {
    assert span != null;
    return span;
  }

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
    }
    return span;
  }
}
