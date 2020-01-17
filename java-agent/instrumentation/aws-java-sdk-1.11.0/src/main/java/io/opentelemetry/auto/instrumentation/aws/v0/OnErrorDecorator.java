package io.opentelemetry.auto.instrumentation.aws.v0;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class OnErrorDecorator extends BaseDecorator {
  public static final OnErrorDecorator DECORATE = new OnErrorDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"aws-sdk"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-aws-sdk";
  }
}
