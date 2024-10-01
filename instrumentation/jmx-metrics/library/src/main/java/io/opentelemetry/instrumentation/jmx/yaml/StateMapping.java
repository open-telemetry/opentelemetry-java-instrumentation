/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** State mapping for "state metrics", contains: */
public class StateMapping {

  private static final StateMapping EMPTY =
      new StateMapping(null, Collections.emptyMap(), Collections.emptySet());

  /** default state to map entries that are not part of {@link #stateMapping} */
  @Nullable private final String defaultStateKey;

  /** maps values (keys) to their respective state (value) */
  private final Map<String, String> stateMapping;

  /** set of all states, including {@link #defaultStateKey} */
  private final Set<String> stateKeys;

  private StateMapping(
      @Nullable String defaultState, Map<String, String> stateMapping, Set<String> stateKeys) {
    this.defaultStateKey = defaultState;
    this.stateMapping = stateMapping;
    this.stateKeys = stateKeys;
  }

  /**
   * Returns {@literal} true when empty, {@literal false} otherwise
   *
   * @return {@literal true} when state mapping is empty, {@literal false} otherwise
   */
  public boolean isEmpty() {
    return stateKeys.isEmpty();
  }

  /**
   * get state keys
   *
   * @return set of state keys, including the default one
   */
  public Set<String> getStateKeys() {
    return stateKeys;
  }

  /**
   * get default state key
   *
   * @return default state key, {@literal null} when empty
   */
  @Nullable
  public String getDefaultStateKey() {
    return defaultStateKey;
  }

  /**
   * Get mapped state value
   *
   * @param rawValue raw state value from JMX attribute
   * @return mapped state value from raw value, default value or {@literal null} when empty
   */
  @Nullable
  public String getStateValue(String rawValue) {
    String value = stateMapping.get(rawValue);
    if (value == null) {
      value = defaultStateKey;
    }
    return value;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Empty instance
   *
   * @return an empty {@link StateMapping} instance
   */
  public static StateMapping empty() {
    return EMPTY;
  }

  public static class Builder {

    private String defaultState;
    private final Map<String, String> valueMapping;
    private final Set<String> stateKeys;

    private Builder() {
      this.valueMapping = new HashMap<>();
      this.stateKeys = new HashSet<>();
    }

    /**
     * Adds default state key
     *
     * @param state state key
     * @return this
     */
    @CanIgnoreReturnValue
    public Builder withDefaultState(String state) {
      if (defaultState != null) {
        throw new IllegalStateException("default state already set");
      }
      defaultState = state;
      stateKeys.add(defaultState);
      return this;
    }

    /**
     * Adds a mapped state value
     *
     * @param value raw value to be mapped
     * @param state state value to map raw value to
     * @return this
     */
    @CanIgnoreReturnValue
    public Builder withMappedValue(String value, String state) {
      String currentMapping = valueMapping.putIfAbsent(value, state);
      if (currentMapping != null) {
        throw new IllegalStateException(value + " already mapped to " + currentMapping);
      }
      stateKeys.add(state);
      return this;
    }

    StateMapping build() {
      if (stateKeys.isEmpty()) {
        return EMPTY;
      }

      if (defaultState == null) {
        throw new IllegalStateException("missing default state");
      }

      return new StateMapping(defaultState, valueMapping, stateKeys);
    }
  }
}
