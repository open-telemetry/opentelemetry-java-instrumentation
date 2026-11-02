/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.Activation;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.ContextState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class FiberContextStorageTest {

  @Test
  void strictScopesEnforceNestingAndOwnerAndCheckLeaks() throws Exception {
    Context parentContext = context("parent");
    Context nestedContext = context("nested");
    ContextStateImpl owner = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage(), true);

    Activation ownerActivation = FiberContextStorageHolder.activate(owner, null);
    try {
      Scope parentScope = storage.attach(parentContext);
      Scope redundantScope = storage.attach(parentContext);
      assertThat(redundantScope).isNotSameAs(Scope.noop());
      redundantScope.close();
      assertThat(storage.current()).isSameAs(parentContext);

      Scope nestedScope = storage.attach(nestedContext);
      assertThatThrownBy(parentScope::close).isInstanceOf(IllegalStateException.class);
      assertThat(storage.current()).isSameAs(nestedContext);

      ContextStateImpl otherFiber = new ContextStateImpl(context("other-fiber"));
      Activation otherActivation = FiberContextStorageHolder.activate(otherFiber, ownerActivation);
      try {
        assertThatThrownBy(nestedScope::close).isInstanceOf(IllegalStateException.class);
        assertThat(storage.current()).isSameAs(otherFiber.getContext());
      } finally {
        FiberContextStorageHolder.deactivate(otherActivation);
      }

      assertThat(storage.current()).isSameAs(nestedContext);
      assertThatThrownBy(storage::close).isInstanceOf(AssertionError.class);
      nestedScope.close();
      parentScope.close();
      storage.close();
    } finally {
      FiberContextStorageHolder.deactivate(ownerActivation);
    }
  }

  @Test
  void nonStrictInvalidClosesAreIgnoredWithoutChangingEitherFiber() throws Exception {
    Context parentContext = context("parent");
    Context nestedContext = context("nested");
    ContextStateImpl owner = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage(), false);
    Logger logger = Logger.getLogger(FiberContextStorage.class.getName());
    Level previousLevel = logger.getLevel();
    LoggingHandler handler = new LoggingHandler();
    logger.setLevel(FINE);
    logger.addHandler(handler);

    Activation ownerActivation = FiberContextStorageHolder.activate(owner, null);
    try {
      Scope parentScope = storage.attach(parentContext);
      assertThat(storage.attach(parentContext)).isSameAs(Scope.noop());
      Scope nestedScope = storage.attach(nestedContext);

      assertThatCode(parentScope::close).doesNotThrowAnyException();
      assertThat(storage.current()).isSameAs(nestedContext);

      ContextStateImpl otherFiber = new ContextStateImpl(context("other-fiber"));
      Activation otherActivation = FiberContextStorageHolder.activate(otherFiber, ownerActivation);
      try {
        assertThatCode(nestedScope::close).doesNotThrowAnyException();
        assertThat(storage.current()).isSameAs(otherFiber.getContext());
      } finally {
        FiberContextStorageHolder.deactivate(otherActivation);
      }

      assertThat(storage.current()).isSameAs(nestedContext);
      assertThat(handler.records).hasSize(2);
      assertThat(handler.records).extracting(LogRecord::getLevel).containsExactly(FINE, FINE);
      assertThat(handler.records.get(0).getMessage()).contains("out of order");
      assertThat(handler.records.get(1).getMessage()).contains("different fiber");
      assertThatCode(storage::close).doesNotThrowAnyException();
      nestedScope.close();
      parentScope.close();
    } finally {
      FiberContextStorageHolder.deactivate(ownerActivation);
      logger.removeHandler(handler);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void nonStrictInvalidCloseDoesNotPropagateLoggingFailures() throws Exception {
    ContextStateImpl owner = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage(), false);
    Logger logger = Logger.getLogger(FiberContextStorage.class.getName());
    Level previousLevel = logger.getLevel();
    LoggingHandler handler = new LoggingHandler();
    handler.failure = new IllegalStateException("handler failure");
    logger.setLevel(FINE);
    logger.addHandler(handler);

    Activation ownerActivation = FiberContextStorageHolder.activate(owner, null);
    try {
      Context ownerContext = context("owner");
      Scope scope = storage.attach(ownerContext);
      ContextStateImpl otherFiber = new ContextStateImpl(context("other"));
      Activation otherActivation = FiberContextStorageHolder.activate(otherFiber, ownerActivation);
      try {
        assertThatCode(scope::close).doesNotThrowAnyException();
        assertThat(storage.current()).isSameAs(otherFiber.getContext());
      } finally {
        FiberContextStorageHolder.deactivate(otherActivation);
      }

      assertThat(storage.current()).isSameAs(ownerContext);
      scope.close();
      assertThat(storage.current()).isSameAs(Context.root());
    } finally {
      FiberContextStorageHolder.deactivate(ownerActivation);
      logger.removeHandler(handler);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void scopeSurvivesDeactivationAndClosesAfterWorkerMigration() throws Exception {
    Context attachedContext = context("attached");
    ContextStateImpl state = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage(), true);
    ExecutorService openingWorker =
        Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "scope-opening-worker"));
    ExecutorService closingWorker =
        Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "scope-closing-worker"));
    AtomicReference<Thread> openingThread = new AtomicReference<>();
    try {
      Scope scope =
          openingWorker
              .submit(
                  () -> {
                    Activation activation = FiberContextStorageHolder.activate(state, null);
                    try {
                      openingThread.set(Thread.currentThread());
                      Scope attached = storage.attach(attachedContext);
                      assertThat(storage.current()).isSameAs(attachedContext);
                      return attached;
                    } finally {
                      FiberContextStorageHolder.deactivate(activation);
                    }
                  })
              .get(10, SECONDS);

      closingWorker
          .submit(
              () -> {
                Activation activation = FiberContextStorageHolder.activate(state, null);
                try {
                  assertThat(Thread.currentThread()).isNotSameAs(openingThread.get());
                  assertThat(storage.current()).isSameAs(attachedContext);
                  scope.close();
                  assertThat(storage.current()).isSameAs(Context.root());
                } finally {
                  FiberContextStorageHolder.deactivate(activation);
                }
              })
          .get(10, SECONDS);

      openingWorker
          .submit(
              () -> {
                Activation activation = FiberContextStorageHolder.activate(state, null);
                try {
                  scope.close();
                  assertThat(storage.current()).isSameAs(Context.root());
                } finally {
                  FiberContextStorageHolder.deactivate(activation);
                }
              })
          .get(10, SECONDS);

      storage.close();
    } finally {
      openingWorker.shutdownNow();
      closingWorker.shutdownNow();
    }
  }

  @Test
  void activationClearsAndCanBeActivatedAgain() {
    ContextStateImpl state = new ContextStateImpl(Context.root());
    assertThat(FiberContextStorageHolder.current()).isNull();

    for (int i = 0; i < 2; i++) {
      Activation activation = FiberContextStorageHolder.activate(state, null);
      try {
        assertThat(FiberContextStorageHolder.isEnabled()).isTrue();
        assertThat(FiberContextStorageHolder.current()).isSameAs(state);
      } finally {
        assertThat(FiberContextStorageHolder.deactivate(activation)).isNull();
      }
      assertThat(FiberContextStorageHolder.current()).isNull();
      assertThat(FiberContextStorageHolder.isEnabled()).isTrue();
    }
  }

  @Test
  void nestedActivationRestoresOuterFiberAndItsScope() throws Exception {
    Context outerContext = context("outer");
    Context innerContext = context("inner");
    ContextStateImpl outerFiber = new ContextStateImpl(Context.root());
    ContextStateImpl innerFiber = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage(), true);

    Activation outerActivation = FiberContextStorageHolder.activate(outerFiber, null);
    try {
      Scope outerScope = storage.attach(outerContext);
      Activation innerActivation = FiberContextStorageHolder.activate(innerFiber, outerActivation);
      try {
        Scope innerScope = storage.attach(innerContext);
        assertThat(storage.current()).isSameAs(innerContext);
        innerScope.close();
      } finally {
        assertThat(FiberContextStorageHolder.deactivate(innerActivation)).isSameAs(outerActivation);
      }

      assertThat(storage.current()).isSameAs(outerContext);
      outerScope.close();
      assertThat(storage.current()).isSameAs(Context.root());
      storage.close();
    } finally {
      assertThat(FiberContextStorageHolder.deactivate(outerActivation)).isNull();
    }
    assertThat(FiberContextStorageHolder.current()).isNull();
  }

  @Test
  void delegatesStorageOperationsWhenNoFiberIsActive() {
    TestStorage delegate = new TestStorage();
    FiberContextStorage storage = new FiberContextStorage(delegate, true);
    Context context = context("ordinary");

    assertThat(storage.current()).isSameAs(delegate.current());
    assertThat(storage.root()).isSameAs(delegate.root());
    Scope scope = storage.attach(context);
    assertThat(scope).isSameAs(delegate.scope);
    assertThat(delegate.attachedContext).isSameAs(context);
    scope.close();
    assertThat(delegate.scopeCloseCount).isEqualTo(1);

    assertThatCode(storage::close).doesNotThrowAnyException();
    assertThat(delegate.closeCount).isEqualTo(1);
  }

  @Test
  void readsStrictModeFromContextStorageProperty() throws Exception {
    String strictContextProperty =
        ContextStorage.class.getPackage().getName() + ".enableStrictContext";
    String originalValue = System.getProperty(strictContextProperty);
    try {
      assertWrongOwnerCloseBehavior(strictContextProperty, "true", true);
      assertWrongOwnerCloseBehavior(strictContextProperty, "false", false);
    } finally {
      if (originalValue == null) {
        System.clearProperty(strictContextProperty);
      } else {
        System.setProperty(strictContextProperty, originalValue);
      }
    }
  }

  private static void assertWrongOwnerCloseBehavior(
      String strictContextProperty, String strictContextValue, boolean strict) throws Exception {
    System.setProperty(strictContextProperty, strictContextValue);
    ContextStateImpl owner = new ContextStateImpl(Context.root());
    FiberContextStorage storage = new FiberContextStorage(new TestStorage());

    Activation ownerActivation = FiberContextStorageHolder.activate(owner, null);
    try {
      Scope scope = storage.attach(context("owner"));
      ContextStateImpl otherFiber = new ContextStateImpl(context("other"));
      Activation otherActivation = FiberContextStorageHolder.activate(otherFiber, ownerActivation);
      try {
        if (strict) {
          assertThatThrownBy(scope::close).isInstanceOf(IllegalStateException.class);
        } else {
          assertThatCode(scope::close).doesNotThrowAnyException();
        }
      } finally {
        FiberContextStorageHolder.deactivate(otherActivation);
      }

      scope.close();
      storage.close();
    } finally {
      FiberContextStorageHolder.deactivate(ownerActivation);
    }
  }

  private static Context context(String value) {
    return Context.root().with(ContextKey.named(value), value);
  }

  private static final class ContextStateImpl implements ContextState {

    private Context context;
    private Object scope;

    private ContextStateImpl(Context context) {
      this.context = context;
    }

    @Override
    public Object getContext() {
      return context;
    }

    @Override
    public void setContext(Object context) {
      this.context = (Context) context;
    }

    @Override
    public Object getScope() {
      return scope;
    }

    @Override
    public void setScope(Object scope) {
      this.scope = scope;
    }
  }

  private static final class TestStorage implements ContextStorage, AutoCloseable {

    private final Context currentContext = context("delegate-current");
    private final Context rootContext = context("delegate-root");
    private int scopeCloseCount;
    private final Scope scope =
        () -> {
          scopeCloseCount++;
        };

    private Context attachedContext;
    private int closeCount;

    @Override
    public Scope attach(Context toAttach) {
      attachedContext = toAttach;
      return scope;
    }

    @Override
    public Context current() {
      return currentContext;
    }

    @Override
    public Context root() {
      return rootContext;
    }

    @Override
    public void close() {
      closeCount++;
    }
  }

  private static final class LoggingHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void publish(LogRecord record) {
      records.add(record);
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
