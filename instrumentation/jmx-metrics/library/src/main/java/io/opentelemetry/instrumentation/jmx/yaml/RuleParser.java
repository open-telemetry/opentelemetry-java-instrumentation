/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

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

  @SuppressWarnings("unchecked")
  public JmxConfig loadConfig(InputStream is) {
    LoadSettings settings = LoadSettings.builder().build();
    Load yaml = new Load(settings);

    Map<String, Object> data = (Map<String, Object>) yaml.loadFromInputStream(is);
    if (data == null) {
      return new JmxConfig(emptyList());
    }

    List<Object> rules = (List<Object>) data.remove("rules");
    if (rules == null) {
      return new JmxConfig(emptyList());
    }

    failOnExtraKeys(data);
    return new JmxConfig(
        rules.stream()
            .map(obj -> (Map<String, Object>) obj)
            .map(RuleParser::parseJmxRule)
            .collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  private static JmxRule parseJmxRule(Map<String, Object> ruleYaml) {
    JmxRule jmxRule = new JmxRule();

    String bean = (String) ruleYaml.remove("bean");
    if (bean != null) {
      jmxRule.setBean(bean);
    }
    List<String> beans = (List<String>) ruleYaml.remove("beans");
    if (beans != null) {
      jmxRule.setBeans(beans);
    }
    String prefix = (String) ruleYaml.remove("prefix");
    if (prefix != null) {
      jmxRule.setPrefix(prefix);
    }
    jmxRule.setMapping(parseMappings((Map<String, Object>) ruleYaml.remove("mapping")));
    parseMetricStructure(ruleYaml, jmxRule);

    failOnExtraKeys(ruleYaml);
    return jmxRule;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Metric> parseMappings(@Nullable Map<String, Object> mappingYaml) {
    Map<String, Metric> mappings = new LinkedHashMap<>();
    if (mappingYaml != null) {
      mappingYaml.forEach(
          (name, metricYaml) -> {
            Metric m = null;
            if (metricYaml != null) {
              m = parseMetric((Map<String, Object>) metricYaml);
            }
            mappings.put(name, m);
          });
    }
    return mappings;
  }

  private static Metric parseMetric(Map<String, Object> metricYaml) {
    Metric metric = new Metric();

    String metricName = (String) metricYaml.remove("metric");
    if (metricName != null) {
      metric.setMetric(metricName);
    }
    String desc = (String) metricYaml.remove("desc");
    if (desc != null) {
      metric.setDesc(desc);
    }
    parseMetricStructure(metricYaml, metric);

    failOnExtraKeys(metricYaml);
    return metric;
  }

  @SuppressWarnings("unchecked")
  private static void parseMetricStructure(
      Map<String, Object> metricStructureYaml, MetricStructure out) {

    String type = (String) metricStructureYaml.remove("type");
    if (type != null) {
      out.setType(type);
    }
    Map<String, Object> metricAttribute =
        (Map<String, Object>) metricStructureYaml.remove("metricAttribute");
    if (metricAttribute != null) {
      out.setMetricAttribute(metricAttribute);
    }
    String unit = (String) metricStructureYaml.remove("unit");
    if (unit != null) {
      out.setUnit(unit);
    }
  }

  private static void failOnExtraKeys(Map<String, Object> yaml) {
    if (!yaml.isEmpty()) {
      throw new IllegalArgumentException("Unrecognized keys found: " + yaml.keySet());
    }
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
      logger.log(INFO, "{0}: found {1} metric rules", new Object[] {id, config.getRules().size()});
      config.addMetricDefsTo(conf);
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
