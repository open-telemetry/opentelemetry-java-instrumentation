package io.opentelemetry.instrumentation.graphql.v17;

import graphql.execution.instrumentation.InstrumentationState;
import io.opentelemetry.context.Context;

public class GraphQLInstrumentationState implements InstrumentationState {

  private final Context context;

  public GraphQLInstrumentationState(Context context) {
    this.context = context;
  }

  public Context getContext() {
    return context;
  }

  @Override
  public String toString() {
    return "GraphQLInstrumentationState{" + "context=" + context + '}';
  }
}
