package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.checkerframework.checker.nullness.qual.Nullable;

final class BuiltInInstrumentationCategories {

  static final InstrumentationCategory NONE = new InstrumentationCategory() {
    @Override
    public @Nullable Span getMatchingSpanOrNull(Context context) {
      return null;
    }

    @Override
    public Context setInContext(Context context, Span clientSpan) {
      return context;
    }

    @Override
    public String toString() {
      return "none";
    }
  };

  private static final String TYPED_CONTEXT_KEY_PREFIX = "opentelemetry-trace-span-key-";

  abstract static class KeyBasedCategory extends InstrumentationCategory {

    private final String name;

    KeyBasedCategory(String name) {
      this.name = name;
    }

    /** This method is expected to always return the same instance. */
    abstract ContextKey<Span> key();

    @Nullable
    public final Span getMatchingSpanOrNull(Context context) {
      return context.get(key());
    }

    public Context setInContext(Context context, Span span) {
      return context.with(key(), span);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  static final class CustomCategory extends KeyBasedCategory {
    private final ContextKey<Span> key;

    CustomCategory(String name) {
      super(name);
      key = ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "custom-" + name);
    }

    @Override
    public ContextKey<Span> key() {
      return key;
    }
  }

  static final class LocalRootCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "local-root");

    static final InstrumentationCategory INSTANCE = new LocalRootCategory();

    private LocalRootCategory() {
      super("local-root");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  static final class ServerCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "server");

    static final InstrumentationCategory INSTANCE = new ServerCategory();

    private ServerCategory() {
      super("server");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  @Deprecated
  static final class ClientCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "client");

    static final InstrumentationCategory INSTANCE = new ClientCategory();

    private ClientCategory() {
      super("client");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  static final class HttpClientCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "http-client");

    static final InstrumentationCategory INSTANCE = new HttpClientCategory();

    private HttpClientCategory() {
      super("http-client");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  static final class DatabaseClientCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "db-client");

    static final InstrumentationCategory INSTANCE = new DatabaseClientCategory();

    private DatabaseClientCategory() {
      super("db-client");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  static final class RpcClientCategory extends KeyBasedCategory {

    private static final ContextKey<Span> KEY =
        ContextKey.named(TYPED_CONTEXT_KEY_PREFIX + "rpc-client");

    static final InstrumentationCategory INSTANCE = new RpcClientCategory();

    private RpcClientCategory() {
      super("rpc-client");
    }

    @Override
    public ContextKey<Span> key() {
      return KEY;
    }
  }

  private BuiltInInstrumentationCategories() {}
}
