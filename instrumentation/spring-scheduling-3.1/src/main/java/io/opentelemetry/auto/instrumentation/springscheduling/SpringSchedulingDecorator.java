package io.opentelemetry.auto.instrumentation.springscheduling;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

@Slf4j
public class SpringSchedulingDecorator extends BaseDecorator {
  public static final SpringSchedulingDecorator DECORATE = new SpringSchedulingDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.spring-scheduling-3.1");

  private SpringSchedulingDecorator() {}

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "spring-scheduling";
  }

  public Span onRun(final Span span, final Runnable runnable) {
    if (runnable != null) {
      String resourceName = "";
      if (runnable instanceof ScheduledMethodRunnable) {
        final ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
        resourceName = spanNameForMethod(scheduledMethodRunnable.getMethod());
      } else {
        final String className = spanNameForClass(runnable.getClass());
        final String methodName = "run";
        resourceName = className + "." + methodName;
      }
      span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    }
    return span;
  }
}
