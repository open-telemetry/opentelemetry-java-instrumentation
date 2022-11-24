/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import java.io.InputStream;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/** Parse a YAML file containing a number of rules. */
public class RuleParser {

  // The YAML parser will create and populate objects of the following classes from the
  // io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.data package:
  // - JmxConfig
  // - JmxRule (a subclass of MetricStructure)
  // - Metric (a subclass of MetricStructure)
  // To populate the objects, the parser will call setter methods for the object fields with
  // whatever comes as the result of parsing the YAML file. This means that the arguments for
  // the setter calls will be non-null, unless the user will explicitly specify the 'null' literal.
  // However, there's hardly any difference in user visible error messages whether the setter
  // throws an IllegalArgumentException, or NullPointerException. Therefore, in all above
  // classes we skip explicit checks for nullnes in the field setters, and let the setters
  // crash with NullPointerException instead.

  private static final Logger logger = Logger.getLogger(RuleParser.class.getName());

  private static final RuleParser theParser = new RuleParser();

  public static RuleParser get() {
    return theParser;
  }

  private RuleParser() {}

  public JmxConfig loadConfig(InputStream is) throws Exception {
    Yaml yaml = new Yaml(new Constructor(JmxConfig.class));
    return yaml.load(is);
  }

  /**
   * Parse the YAML rules from the specified input stream and add them, after converting to the
   * internal representation, to the provided metric configuration.
   *
   * @param conf the metric configuration
   * @param is the InputStream with the YAML rules
   * @param id identifier of the YAML ruleset, such as a filename
   */
  public void addMetricDefsTo(MetricConfiguration conf, InputStream is, String id) {
    try {

      JmxConfig config = loadConfig(is);
      if (config != null) {
        logger.log(
            INFO, "{0}: found {1} metric rules", new Object[] {id, config.getRules().size()});
        config.addMetricDefsTo(conf);
      }
    } catch (Exception exception) {
      logger.log(
          WARNING,
          "Failed to parse YAML rules from {0}: {1}",
          new Object[] {id, rootCause(exception)});
      // It is essential that the parser exception is made visible to the user.
      // It contains contextual information about any syntax issues found by the parser.
      logger.log(WARNING, exception.toString());
    }
  }

  /**
   * Given an exception thrown by the parser, try to find the original cause of the problem.
   *
   * @param exception the exception thrown by the parser
   * @return a String describing the probable root cause
   */
  private static String rootCause(Throwable exception) {
    String rootClass = "";
    String message = null;
    // Go to the bottom of it
    for (; exception != null; exception = exception.getCause()) {
      rootClass = exception.getClass().getSimpleName();
      message = exception.getMessage();
    }
    return message == null ? rootClass : message;
  }
}
