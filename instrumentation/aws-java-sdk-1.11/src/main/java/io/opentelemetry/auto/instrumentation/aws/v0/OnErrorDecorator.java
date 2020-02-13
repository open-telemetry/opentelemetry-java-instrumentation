package io.opentelemetry.auto.instrumentation.aws.v0;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class OnErrorDecorator extends BaseDecorator {
  public static final OnErrorDecorator DECORATE = new OnErrorDecorator();

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "java-aws-sdk";
  }
}
