/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

// MutableConfigProvider: If MutableConfigProvider was supported, this registry could be enhanced to:
// - Register as a listener with the MutableConfigProvider to receive immediate notifications on config changes
// - Eliminate the need for periodic polling (executor) and instead react to provider-driven change events
// - Support dynamic config updates without the 30-second polling delay

/**
 * Registry for callbacks that are invoked when configuration option values change.
 *
 * <p>This singleton can be loaded by multiple classloaders, creating separate instances that each monitor
 * the globally consistent System properties. For instances that don't need periodic checking (e.g., only
 * used for {@link #updateOption(String, String)}), {@link #shutdownPeriodicChecker()} can be called
 * to stop the background thread. Typically this would mean the extension that loads this class to use
 * {@link #updateOption(String, String)}) and no other capability of this class, should shutdown the
 * periodic checker during the extension initialization task. If this is not done, there are no adverse
 * effects other than the additional very low overhead thread.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OptionCallbackRegistry {

  private static final Logger logger = Logger.getLogger(OptionCallbackRegistry.class.getName());

  private static final OptionCallbackRegistry INSTANCE = new OptionCallbackRegistry();

  // Allow multiple registrations on any key
  private final Map<String, List<OptionChangeListener>> callbacks = new ConcurrentHashMap<>();

  // Keep previous values so that only changes get notified
  private final Map<String, String> previousValues = new ConcurrentHashMap<>();

  // MutableConfigProvider: This executor could be unnecessary if MutableConfigProvider was available,
  // as we could register directly with the provider for immediate change notifications instead of polling
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "otel-option-callback-checker");
            t.setDaemon(true);
            return t;
          });

  private OptionCallbackRegistry() {
    // Start periodic checking
    // MutableConfigProvider: Instead of polling, register with MutableConfigProvider like:
    // mutableConfigProvider.addChangeListener(this::onConfigChanged);
    executor.scheduleWithFixedDelay(this::checkForChanges, 30, 30, TimeUnit.SECONDS);
  }

  /** Returns the global OptionCallbackRegistry instance. */
  public static OptionCallbackRegistry getInstance() {
    return INSTANCE;
  }

  /** Shuts down the periodic checking executor. */
  public void shutdownPeriodicChecker() {
    executor.shutdown();
  }

  /**
  * Registers a listener to be invoked when the value of the specified option key changes.
  *
  * <p>The listener receives the key and the new value. If the value becomes null, the listener is
  * still invoked.
  *
  * <p>MutableConfigProvider: With MutableConfigProvider, this method would register the listener
  * directly with the provider for the specific key, ensuring immediate notification of changes
  * without relying on polling. 
  *
  * @param key the configuration key to monitor
  * @param currentValue the current value of the key
  * @param listener the listener to invoke on change
  * @param isDeclarative if true, the currentValue is passed into {@link #updateOption(String, String)}. Where the config is declarative, this should be true, otherwise this should be false
  */
  public void registerCallback(String key, String currentValue, OptionChangeListener listener, boolean isDeclarative) {
    // If instrumentations could be unloaded, we would wrap listeners in a WeakReference to prevent memory leaks:
    // callbacks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(new WeakReference<>(listener));

    callbacks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    if (isDeclarative) {
      updateOption(key, currentValue);
    }
    previousValues.put(key, currentValue);
  }

  /**
  * Updates the value of an option. Registered callbacks will be notified asynchronously
  * when the periodic check detects the change.
  *
  * <p>MutableConfigProvider: If MutableConfigProvider was available, this method would be
  * replaced by provider-driven updates. The MutableConfigProvider could notify this registry
  * directly when configurations change, eliminating the need for manual updates via this method.
  * Alternatively or additionally, this method could remain the primary runtime update mechanism,
  * handling the required updates to MutableConfigProvider.
  *
  * @param key the option key
  * @param value the new value, or {@code null} to remove the option
  */
  public void updateOption(String key, String value) {
    if (value != null) {
      // System.setProperty is thread-safe and provides consistent writes so no need to synchronize
      System.setProperty(key, value);
    } else {
      // Remove the property if value is null
      // System.clearProperty is thread-safe and provides consistent writes so no need to synchronize
      System.clearProperty(key);
    }
  }

  // MutableConfigProvider: This polling method could be replaced by direct callback from MutableConfigProvider.
  // The provider could call a method like onConfigChanged(String key, String newValue) directly,
  // eliminating the need to check all keys periodically and providing immediate updates.
  private void checkForChanges() {
    for (String key : callbacks.keySet()) {
      String currentValue = getCurrentValue(key);
      String previousValue = previousValues.get(key);
      if (!java.util.Objects.equals(currentValue, previousValue)) {
        previousValues.put(key, currentValue);
        notifyCallbacks(key, currentValue, previousValue);
      }
    }
  }

  private static String getCurrentValue(String key) {
    // System.getProperty is thread-safe and provides consistent reads so no need to synchronize
    return System.getProperty(key);
  }

  private void notifyCallbacks(String key, @Nullable String newValue, @Nullable String oldValue) {
    List<OptionChangeListener> listenerList = callbacks.get(key);
    if (listenerList != null) {
      // If we were using WeakReferences, this would need cleanup logic:
      // listenerList.removeIf(ref -> ref.get() == null);
      for (OptionChangeListener listener : listenerList) {
        try {
          listener.onOptionChanged(key, newValue, oldValue);
        } catch (Throwable t) {
          logger.info("Warning, exception thrown when trying to notify listener for key '" + key + "': " + t.getMessage());
        }
      }
    }
  }
}
