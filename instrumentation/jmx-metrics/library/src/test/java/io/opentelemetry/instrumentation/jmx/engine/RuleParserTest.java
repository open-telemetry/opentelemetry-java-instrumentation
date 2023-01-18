/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

// This test is put in the io.opentelemetry.instrumentation.jmx.engine package
// because it needs to access package-private methods from a number of classes.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.jmx.yaml.JmxConfig;
import io.opentelemetry.instrumentation.jmx.yaml.JmxRule;
import io.opentelemetry.instrumentation.jmx.yaml.Metric;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuleParserTest {
  private static RuleParser parser;

  @BeforeAll
  static void setup() {
    parser = RuleParser.get();
    assertThat(parser == null).isFalse();
  }

  /*
   * General syntax
   */
  private static final String CONF2 =
      "---\n"
          + "rules:\n"
          + "  - beans:\n"
          + "      - OBJECT:NAME1=*\n"
          + "      - OBJECT:NAME2=*\n"
          + "    metricAttribute:\n"
          + "      LABEL_KEY1: param(PARAMETER)\n"
          + "      LABEL_KEY2: beanattr(ATTRIBUTE)\n"
          + "    prefix: METRIC_NAME_PREFIX\n"
          + "    mapping:\n"
          + "      ATTRIBUTE1:\n"
          + "        metric: METRIC_NAME1\n"
          + "        type: Gauge\n"
          + "        desc: DESCRIPTION1\n"
          + "        unit: UNIT1\n"
          + "        metricAttribute:\n"
          + "          LABEL_KEY3: const(CONSTANT)\n"
          + "      ATTRIBUTE2:\n"
          + "        metric: METRIC_NAME2\n"
          + "        desc: DESCRIPTION2\n"
          + "        unit: UNIT2\n"
          + "      ATTRIBUTE3:\n"
          + "      ATTRIBUTE4:\n"
          + "  - beans:\n"
          + "      - OBJECT:NAME3=*\n"
          + "    mapping:\n"
          + "      ATTRIBUTE3:\n"
          + "        metric: METRIC_NAME3\n";

  @Test
  void testConf2() {
    InputStream is = new ByteArrayInputStream(CONF2.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(2);

    JmxRule def1 = defs.get(0);
    assertThat(def1.getBeans()).hasSize(2);
    assertThat(def1.getMetricAttribute()).hasSize(2);

    Map<String, Metric> attr = def1.getMapping();
    assertThat(attr).hasSize(4);

    Metric m1 = attr.get("ATTRIBUTE1");
    assertThat(m1).isNotNull();
    assertThat(m1.getMetric()).isEqualTo("METRIC_NAME1");
    assertThat(m1.getMetricType()).isEqualTo(MetricInfo.Type.GAUGE);
    assertThat(m1.getUnit()).isEqualTo("UNIT1");
    assertThat(m1.getMetricAttribute()).containsExactly(entry("LABEL_KEY3", "const(CONSTANT)"));
  }

  private static final String CONF3 =
      "rules:\n"
          + "  - bean: OBJECT:NAME3=*\n"
          + "    mapping:\n"
          + "      ATTRIBUTE31:\n"
          + "      ATTRIBUTE32:\n"
          + "      ATTRIBUTE33:\n"
          + "      ATTRIBUTE34:\n"
          + "        metric: METRIC_NAME34\n"
          + "      ATTRIBUTE35:\n";

  @Test
  void testConf3() {
    InputStream is = new ByteArrayInputStream(CONF3.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(1);

    JmxRule def1 = defs.get(0);
    assertThat(def1.getBean()).isNotNull();
    assertThat(def1.getMetricAttribute()).isNull();

    Map<String, Metric> attr = def1.getMapping();
    assertThat(attr).hasSize(5).containsKey("ATTRIBUTE33");
    assertThat(attr.get("ATTRIBUTE33")).isNull();
    assertThat(attr.get("ATTRIBUTE34")).isNotNull();
  }

  /*
   * Semantics
   */
  private static final String CONF4 =
      "---\n"
          + "rules:\n"
          + "  - bean: my-test:type=4\n"
          + "    metricAttribute:\n"
          + "      LABEL_KEY1: param(PARAMETER)\n"
          + "      LABEL_KEY2: beanattr(ATTRIBUTE)\n"
          + "    prefix: PREFIX.\n"
          + "    type: upDownCounter\n"
          + "    unit: DEFAULT_UNIT\n"
          + "    mapping:\n"
          + "      A.b:\n"
          + "        metric: METRIC_NAME1\n"
          + "        type: counter\n"
          + "        desc: DESCRIPTION1\n"
          + "        unit: UNIT1\n"
          + "        metricAttribute:\n"
          + "          LABEL_KEY3: const(CONSTANT)\n"
          + "      ATTRIBUTE2:\n"
          + "        metric: METRIC_NAME2\n"
          + "        desc: DESCRIPTION2\n"
          + "        unit: UNIT2\n"
          + "      ATTRIBUTE3:\n";

  @Test
  void testConf4() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF4.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(1);

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef).isNotNull();
    assertThat(metricDef.getMetricExtractors()).hasSize(3);

    assertThat(metricDef.getMetricExtractors())
        .anySatisfy(
            m -> {
              assertThat(m.getMetricValueExtractor().getAttributeName()).isEqualTo("A.b");
              assertThat(m.getAttributes()).hasSize(3);

              MetricInfo mb1 = m.getInfo();
              assertThat(mb1.getMetricName()).isEqualTo("PREFIX.METRIC_NAME1");
              assertThat(mb1.getDescription()).isEqualTo("DESCRIPTION1");
              assertThat(mb1.getUnit()).isEqualTo("UNIT1");
              assertThat(mb1.getType()).isEqualTo(MetricInfo.Type.COUNTER);
            })
        .anySatisfy(
            m -> {
              assertThat(m.getMetricValueExtractor().getAttributeName()).isEqualTo("ATTRIBUTE3");

              MetricInfo mb3 = m.getInfo();
              assertThat(mb3.getMetricName()).isEqualTo("PREFIX.ATTRIBUTE3");
              // syntax extension - defining a default unit and type
              assertThat(mb3.getType()).isEqualTo(MetricInfo.Type.UPDOWNCOUNTER);
              assertThat(mb3.getUnit()).isEqualTo("DEFAULT_UNIT");
            });
  }

  private static final String CONF5 = // minimal valid definition
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=5\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n";

  @Test
  void testConf5() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF5.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(1);

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef).isNotNull();
    assertThat(metricDef.getMetricExtractors()).hasSize(1);

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    assertThat(m1.getMetricValueExtractor().getAttributeName()).isEqualTo("ATTRIBUTE");
    assertThat(m1.getAttributes()).isEmpty();

    MetricInfo mb1 = m1.getInfo();
    assertThat(mb1.getMetricName()).isEqualTo("ATTRIBUTE");
    assertThat(mb1.getType()).isEqualTo(MetricInfo.Type.GAUGE);
    assertThat(mb1.getUnit()).isNull();
  }

  private static final String CONF6 = // merging metric attribute sets with same keys
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=6\n"
          + "    metricAttribute:\n"
          + "      key1: const(value1)\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n"
          + "        metricAttribute:\n"
          + "          key1: const(value2)\n";

  @Test
  void testConf6() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF6.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(1);

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef).isNotNull();
    assertThat(metricDef.getMetricExtractors()).hasSize(1);

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    assertThat(m1.getMetricValueExtractor().getAttributeName()).isEqualTo("ATTRIBUTE");
    // MetricAttribute set at the metric level should override the one set at the definition level
    assertThat(m1.getAttributes()).hasSize(1);
    assertThat(m1.getInfo().getMetricName()).isEqualTo("ATTRIBUTE");

    MetricAttribute l1 = m1.getAttributes()[0];
    assertThat(l1.acquireAttributeValue(null, null)).isEqualTo("value2");
  }

  private static final String CONF7 =
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=7\n"
          + "    metricAttribute:\n"
          + "      key1: const(value1)\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n"
          + "        metricAttribute:\n"
          + "          key2: const(value2)\n";

  @Test
  void testConf7() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF7.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config).isNotNull();

    List<JmxRule> defs = config.getRules();
    assertThat(defs).hasSize(1);

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef).isNotNull();
    assertThat(metricDef.getMetricExtractors()).hasSize(1);

    // Test that the MBean attribute is correctly parsed
    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    assertThat(m1.getMetricValueExtractor().getAttributeName()).isEqualTo("ATTRIBUTE");
    assertThat(m1.getAttributes()).hasSize(2);
    assertThat(m1.getInfo().getMetricName()).isEqualTo("ATTRIBUTE");
  }

  private static final String EMPTY_CONF = "---\n";

  @Test
  void testEmptyConf() {
    InputStream is = new ByteArrayInputStream(EMPTY_CONF.getBytes(StandardCharsets.UTF_8));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config.getRules()).isEmpty();
  }

  /*
   *     Negative tests
   */

  private static void runNegativeTest(String yaml) {
    InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));

    Assertions.assertThrows(
        Exception.class,
        () -> {
          JmxConfig config = parser.loadConfig(is);
          assertThat(config).isNotNull();

          List<JmxRule> defs = config.getRules();
          assertThat(defs).hasSize(1);
          defs.get(0).buildMetricDef();
        });
  }

  @Test
  void testNoBeans() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:                  # no bean\n"
            + "  - mapping:           # still no beans\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidObjectName() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: BAD_OBJECT_NAME\n"
            + "    mapping:\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyMapping() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n "
            + "rules:\n"
            + "  - bean: domain:type=6\n"
            + "    mapping:\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidAttributeName() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      .used:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidTag() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        metricAttribute:\n"
            + "          LABEL: something\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidType() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        type: gage\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidTagFromAttribute() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        metricAttribute:\n"
            + "          LABEL: beanattr(.used)\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyTagFromAttribute() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        metricAttribute:\n"
            + "          LABEL: beanattr( )\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyTagFromParameter() {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        metricAttribute:\n"
            + "          LABEL: param( )\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testTypoInMetric() {
    String yaml =
        "---\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      A:\n"
            + "        metrics: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testMessedUpSyntax() {
    String yaml =
        "---\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      metricAttribute:     # not valid here\n"
            + "        key: const(value)\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }
}
