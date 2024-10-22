/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.lang.invoke.MutableCallSite;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

class AdviceBootstrapState implements AutoCloseable {

  private static final ThreadLocal<Map<Key, AdviceBootstrapState>> stateForCurrentThread =
      ThreadLocal.withInitial(HashMap::new);

  private final Key key;
  private int recursionDepth;
  @Nullable private MutableCallSite nestedCallSite;

  /**
   * We have to eagerly initialize to not cause a lambda construction during {@link #enter(Class,
   * String, String, String, String)}.
   */
  private static final Function<Key, AdviceBootstrapState> CONSTRUCTOR = AdviceBootstrapState::new;

  private AdviceBootstrapState(Key key) {
    this.key = key;
    // enter will increment it by one, so 0 is the value for non-recursive calls
    recursionDepth = -1;
  }

  static void initialize() {
    // Eager initialize everything because we could run into recursions doing this during advice
    // bootstrapping
    stateForCurrentThread.get();
    stateForCurrentThread.remove();
    try {
      Class.forName(Key.class.getName());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  static AdviceBootstrapState enter(
      Class<?> instrumentedClass,
      String moduleClassName,
      String adviceClassName,
      String adviceMethodName,
      String adviceMethodDescriptor) {
    Key key =
        new Key(
            instrumentedClass,
            moduleClassName,
            adviceClassName,
            adviceMethodName,
            adviceMethodDescriptor);
    AdviceBootstrapState state = stateForCurrentThread.get().computeIfAbsent(key, CONSTRUCTOR);
    state.recursionDepth++;
    return state;
  }

  public boolean isNestedInvocation() {
    return recursionDepth > 0;
  }

  public MutableCallSite getOrInitMutableCallSite(Supplier<MutableCallSite> initializer) {
    if (nestedCallSite == null) {
      nestedCallSite = initializer.get();
    }
    return nestedCallSite;
  }

  public void initMutableCallSite(MutableCallSite mutableCallSite) {
    if (nestedCallSite != null) {
      throw new IllegalStateException("callsite has already been initialized");
    }
    nestedCallSite = mutableCallSite;
  }

  @Nullable
  public MutableCallSite getMutableCallSite() {
    return nestedCallSite;
  }

  @Override
  public void close() {
    if (recursionDepth == 0) {
      Map<Key, AdviceBootstrapState> stateMap = stateForCurrentThread.get();
      stateMap.remove(key);
      if (stateMap.isEmpty()) {
        // Do not leave an empty map dangling as thread local
        stateForCurrentThread.remove();
      }
    } else {
      recursionDepth--;
    }
  }

  /** Key uniquely identifying a single invokedynamic instruction inserted for an advice */
  private static class Key {

    private final Class<?> instrumentedClass;
    private final String moduleClassName;
    private final String adviceClassName;
    private final String adviceMethodName;
    private final String adviceMethodDescriptor;

    private Key(
        Class<?> instrumentedClass,
        String moduleClassName,
        String adviceClassName,
        String adviceMethodName,
        String adviceMethodDescriptor) {
      this.instrumentedClass = instrumentedClass;
      this.moduleClassName = moduleClassName;
      this.adviceClassName = adviceClassName;
      this.adviceMethodName = adviceMethodName;
      this.adviceMethodDescriptor = adviceMethodDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || !(o instanceof Key)) {
        return false;
      }

      Key that = (Key) o;
      return instrumentedClass.equals(that.instrumentedClass)
          && moduleClassName.equals(that.moduleClassName)
          && adviceClassName.equals(that.adviceClassName)
          && adviceMethodName.equals(that.adviceMethodName)
          && adviceMethodDescriptor.equals(that.adviceMethodDescriptor);
    }

    @Override
    public int hashCode() {
      int result = instrumentedClass.hashCode();
      result = 31 * result + moduleClassName.hashCode();
      result = 31 * result + adviceClassName.hashCode();
      result = 31 * result + adviceMethodName.hashCode();
      result = 31 * result + adviceMethodDescriptor.hashCode();
      return result;
    }
  }
}
