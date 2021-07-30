package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class InstrumentationCategory {

  public static InstrumentationCategory none() {
    return BuiltInInstrumentationCategories.NONE;
  }

  public static InstrumentationCategory localRoot() {
    return BuiltInInstrumentationCategories.LocalRootCategory.INSTANCE;
  }

  // TODO: a server span is always a localRoot span; these two should be connected together; same
  // for consumer spans
  // or should we just get rid of server() and just use localRoot() everywhere? that'd simplify some
  // logic for sure

  // alternatively: local root spans could be marked/checked by the ServerInstrumenter (consider
  // renaming to sth like UpstreamPropagatingInstrumenter) - it's a good place to apply this
  // automagically without manual intervention from the instrumentation author
  public static InstrumentationCategory server() {
    return BuiltInInstrumentationCategories.ServerCategory.INSTANCE;
  }

  /** Same as old {@link io.opentelemetry.instrumentation.api.tracer.ClientSpan}, to be removed. */
  @Deprecated
  public static InstrumentationCategory client() {
    return BuiltInInstrumentationCategories.LocalRootCategory.INSTANCE;
  }

  public static InstrumentationCategory httpClient() {
    return BuiltInInstrumentationCategories.HttpClientCategory.INSTANCE;
  }

  public static InstrumentationCategory databaseClient() {
    return BuiltInInstrumentationCategories.DatabaseClientCategory.INSTANCE;
  }

  public static InstrumentationCategory rpcClient() {
    return BuiltInInstrumentationCategories.RpcClientCategory.INSTANCE;
  }

  // each call to this method creates a unique instrumentation type
  // name is only used for debugging
  public static InstrumentationCategory custom(String name) {
    return new BuiltInInstrumentationCategories.CustomCategory(name);
  }

  // package private constructor to limit implementations
  InstrumentationCategory() {}

  public final boolean matches(Context context) {
    return getMatchingSpanOrNull(context) != null;
  }

  // TODO: could it be not public?
  @Nullable
  public abstract Span getMatchingSpanOrNull(Context context);

  public abstract Context setInContext(Context context, Span clientSpan);
}
