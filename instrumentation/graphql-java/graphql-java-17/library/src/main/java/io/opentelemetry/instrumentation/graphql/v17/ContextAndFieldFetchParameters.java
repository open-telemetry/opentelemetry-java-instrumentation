package io.opentelemetry.instrumentation.graphql.v17;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.opentelemetry.context.Context;

class ContextAndFieldFetchParameters {

  private final Context context;
  private final InstrumentationFieldFetchParameters fieldFetchParameters;

  ContextAndFieldFetchParameters(
      Context context, InstrumentationFieldFetchParameters fieldFetchParameters) {
    this.context = context;
    this.fieldFetchParameters = fieldFetchParameters;
  }

  public Context getContext() {
    return context;
  }

  public InstrumentationFieldFetchParameters getFieldFetchParameters() {
    return fieldFetchParameters;
  }
}
