package io.opentelemetry.auto.instrumentation.otapi;

import unshaded.io.opentelemetry.context.Scope;

public class UnshadedScope implements Scope {

  private final io.opentelemetry.context.Scope shadedScope;

  public UnshadedScope(final io.opentelemetry.context.Scope shadedScope) {
    this.shadedScope = shadedScope;
  }

  @Override
  public void close() {
    shadedScope.close();
  }
}
