package com.datadoghq.trace.resolver;

import com.datadoghq.trace.integration.DDSpanContextDecorator;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Create DDSpaDecorators from a valid configuration */
public class DDDecoratorsFactory {

  private static final Logger logger = LoggerFactory.getLogger(DDDecoratorsFactory.class);

  public static String DECORATORS_PACKAGE = "com.datadoghq.trace.integration.";

  public static final String CONFIG_PATH = "dd-trace-decorators.yaml";

  /**
   * Create decorators from configuration
   *
   * @param decoratorsConfig
   * @return the list of instanciated and configured decorators
   */
  public static List<DDSpanContextDecorator> create(
      final List<DDSpanDecoratorConfig> decoratorsConfig) {
    final List<DDSpanContextDecorator> decorators = new ArrayList<>();
    for (final DDSpanDecoratorConfig decoratorConfig : decoratorsConfig) {
      if (decoratorConfig.getType() == null) {
        logger.warn("Cannot create decorator without type from configuration {}", decoratorConfig);
        continue;
      }

      //Find class and create
      final Class<?> decoratorClass;
      try {
        decoratorClass = Class.forName(DECORATORS_PACKAGE + decoratorConfig.getType());
      } catch (final ClassNotFoundException e) {
        logger.warn(
            "Cannot create decorator as the class {} is not defined. Provided configuration {}",
            decoratorConfig);
        continue;
      }

      DDSpanContextDecorator decorator = null;
      try {
        decorator = (DDSpanContextDecorator) decoratorClass.getConstructor().newInstance();
      } catch (final Exception e) {
        logger.warn(
            "Cannot create decorator as we could not invoke the default constructor. Provided configuration {}",
            decoratorConfig);
        continue;
      }

      //Fill with config values
      if (decoratorConfig.getMatchingTag() != null) {
        decorator.setMatchingTag(decoratorConfig.getMatchingTag());
      }
      if (decoratorConfig.getMatchingValue() != null) {
        decorator.setMatchingValue(decoratorConfig.getMatchingValue());
      }
      if (decoratorConfig.getSetTag() != null) {
        decorator.setSetTag(decoratorConfig.getSetTag());
      }
      if (decoratorConfig.getSetValue() != null) {
        decorator.setSetValue(decoratorConfig.getSetValue());
      }

      decorators.add(decorator);
    }
    return decorators;
  }

  public static List<DDSpanContextDecorator> createFromResources() {
    List<DDSpanContextDecorator> result = new ArrayList<>();
    final TracerConfig config =
        FactoryUtils.loadConfigFromResource(CONFIG_PATH, TracerConfig.class);
    if (config != null) {
      result = DDDecoratorsFactory.create(config.getDecorators());
    }
    return result;
  }
}
