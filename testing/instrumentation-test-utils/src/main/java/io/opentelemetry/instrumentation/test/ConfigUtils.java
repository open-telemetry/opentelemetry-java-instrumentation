package io.opentelemetry.instrumentation.test;

import io.opentelemetry.instrumentation.api.config.Config;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Utilities for changin {@link Config} during instrumentation tests.
 */
public final class ConfigUtils {

  private static final MethodHandle setConfigInstance;

  static {
    try {
      Field configInstance = Config.class.getDeclaredField("INSTANCE");
      configInstance.setAccessible(true);
      setConfigInstance = MethodHandles.lookup().unreflectSetter(configInstance);
    } catch (Throwable t) {
      throw new Error("Could not access Config.INSTANCE through reflection.");
    }
  }

  /**
   * Allows the caller to modify (add property, remove property, etc) currently used configuration
   * and then sets the current {@code Config.INSTANCE} singleton value to the modified config.
   *
   * @return Previous configuration.
   */
  public synchronized static Config updateConfig(Consumer<Properties> configModifications) {
    Properties properties = new Properties();
    Config.get().asJavaProperties().forEach(properties::put);
    configModifications.accept(properties);

    Map<String, String> newConfig = new HashMap<>();
    properties.forEach((key, value) -> newConfig.put((String) key, (String) value));
    return setConfig(Config.create(newConfig));
  }

  /**
   * Sets {@code Config.INSTANCE} singleton value.
   *
   * @return Previous configuration.
   */
  public synchronized static Config setConfig(Config config) {
    Config previous = Config.get();
    try {
      setConfigInstance.invokeExact(config);
    } catch (Throwable t) {
      throw new Error("Could not invoke setter for Config.INSTANCE via reflection.", t);
    }
    return previous;
  }
}
