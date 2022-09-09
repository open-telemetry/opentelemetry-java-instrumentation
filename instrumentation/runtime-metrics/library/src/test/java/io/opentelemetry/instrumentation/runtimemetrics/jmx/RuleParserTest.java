/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.data.JmxConfig;
import io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.data.JmxRule;
import io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.data.Metric;
import io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.yaml.RuleParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuleParserTest {
  private static RuleParser parser;

  @BeforeAll
  static void setup() throws Exception {
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
          + "    label:\n"
          + "      LABEL_KEY1: param(PARAMETER)\n"
          + "      LABEL_KEY2: attrib(ATTRIBUTE)\n"
          + "    prefix: METRIC_NAME_PREFIX\n"
          + "    mapping:\n"
          + "      ATTRIBUTE1:\n"
          + "        metric: METRIC_NAME1\n"
          + "        type: Gauge\n"
          + "        desc: DESCRIPTION1\n"
          + "        unit: UNIT1\n"
          + "        label:\n"
          + "          LABEL_KEY3: CONSTANT\n"
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
  void testConf2() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF2.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 2).isTrue();

    JmxRule def1 = defs.get(0);
    assertThat(def1.getBeans().size() == 2).isTrue();
    assertThat(def1.getLabel().size() == 2).isTrue();
    Map<String, Metric> attr = def1.getMapping();
    assertThat(attr == null).isFalse();
    assertThat(attr.size() == 4).isTrue();

    Metric m1 = attr.get("ATTRIBUTE1");
    assertThat(m1 == null).isFalse();
    assertThat("METRIC_NAME1".equals(m1.getMetric())).isTrue();
    assertThat(m1.getMetricType() == MetricBanner.Type.GAUGE).isTrue();
    assertThat("UNIT1".equals(m1.getUnit())).isTrue();
    assertThat(m1.getLabel() == null).isFalse();
    assertThat(m1.getLabel().size() == 1).isTrue();
    assertThat("CONSTANT".equals(m1.getLabel().get("LABEL_KEY3"))).isTrue();
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
  void testConf3() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF3.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 1).isTrue();

    JmxRule def1 = defs.get(0);
    assertThat(def1.getBean() == null).isFalse();
    assertThat(def1.getLabel() == null).isTrue();
    Map<String, Metric> attr = def1.getMapping();
    assertThat(attr.size() == 5).isTrue();

    Set<String> keys = attr.keySet();
    assertThat(keys.contains("ATTRIBUTE33")).isTrue();
    assertThat(attr.get("ATTRIBUTE33") == null).isTrue();
    assertThat(attr.get("ATTRIBUTE34") == null).isFalse();
  }

  /*
   * Semantics
   */
  private static final String CONF4 =
      "---\n"
          + "rules:\n"
          + "  - bean: my-test:type=4\n"
          + "    label:\n"
          + "      LABEL_KEY1: param(PARAMETER)\n"
          + "      LABEL_KEY2: attrib(ATTRIBUTE)\n"
          + "    prefix: PREFIX.\n"
          + "    type: upDownCounter\n"
          + "    unit: DEFAULT_UNIT\n"
          + "    mapping:\n"
          + "      A.b:\n"
          + "        metric: METRIC_NAME1\n"
          + "        type: counter\n"
          + "        desc: DESCRIPTION1\n"
          + "        unit: UNIT1\n"
          + "        label:\n"
          + "          LABEL_KEY3: CONSTANT\n"
          + "      ATTRIBUTE2:\n"
          + "        metric: METRIC_NAME2\n"
          + "        desc: DESCRIPTION2\n"
          + "        unit: UNIT2\n"
          + "      ATTRIBUTE3:\n";

  @Test
  void testConf4() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF4.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 1).isTrue();

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef == null).isFalse();
    assertThat(metricDef.getMetricExtractors().length == 3).isTrue();

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    AttributeValueExtractor a1 = m1.getMetricValueExtractor();
    assertThat("A.b".equals(a1.getAttributeName())).isTrue();
    assertThat(m1.getLabels().length == 3).isTrue();
    MetricBanner mb1 = m1.getBanner();
    assertThat("PREFIX.METRIC_NAME1".equals(mb1.getMetricName())).isTrue();
    assertThat("DESCRIPTION1".equals(mb1.getDescription())).isTrue();
    assertThat("UNIT1".equals(mb1.getUnit())).isTrue();
    assertThat(MetricBanner.Type.COUNTER == mb1.getType()).isTrue();

    MetricExtractor m3 = metricDef.getMetricExtractors()[2];
    AttributeValueExtractor a3 = m3.getMetricValueExtractor();
    assertThat("ATTRIBUTE3".equals(a3.getAttributeName())).isTrue();
    MetricBanner mb3 = m3.getBanner();
    assertThat("PREFIX.ATTRIBUTE3".equals(mb3.getMetricName())).isTrue();
    // syntax extension - defining a default unit and type
    assertThat(MetricBanner.Type.UPDOWNCOUNTER == mb3.getType()).isTrue();
    assertThat("DEFAULT_UNIT".equals(mb3.getUnit())).isTrue();
  }

  private static final String CONF5 = // minimal valid definition
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=5\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n";

  @Test
  void testConf5() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF5.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 1).isTrue();

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef == null).isFalse();
    assertThat(metricDef.getMetricExtractors().length == 1).isTrue();

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    AttributeValueExtractor a1 = m1.getMetricValueExtractor();
    assertThat("ATTRIBUTE".equals(a1.getAttributeName())).isTrue();
    assertThat(m1.getLabels().length == 0).isTrue();
    MetricBanner mb1 = m1.getBanner();
    assertThat("ATTRIBUTE".equals(mb1.getMetricName())).isTrue();
    assertThat(MetricBanner.Type.GAUGE == mb1.getType()).isTrue();
    assertThat(null == mb1.getUnit()).isTrue();
  }

  private static final String CONF6 = // merging label sets with same keys
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=6\n"
          + "    label:\n"
          + "      key1: value1\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n"
          + "        label:\n"
          + "          key1: value2\n";

  @Test
  void testConf6() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF6.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 1).isTrue();

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef == null).isFalse();
    assertThat(metricDef.getMetricExtractors().length == 1).isTrue();

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    AttributeValueExtractor a1 = m1.getMetricValueExtractor();
    assertThat("ATTRIBUTE".equals(a1.getAttributeName())).isTrue();
    // MetricLabel set at the metric level should override the one set at the definition level
    assertThat(m1.getLabels().length == 1).isTrue();
    MetricLabel l1 = m1.getLabels()[0];
    assertThat("value2".equals(l1.extractLabelValue(null, null))).isTrue();
    MetricBanner mb1 = m1.getBanner();
    assertThat("ATTRIBUTE".equals(mb1.getMetricName())).isTrue();
  }

  private static final String CONF7 = // merging label sets with different keys
      "---                                   # keep stupid spotlessJava at bay\n"
          + "rules:\n"
          + "  - bean: my-test:type=7\n"
          + "    label:\n"
          + "      key1: value1\n"
          + "    mapping:\n"
          + "      ATTRIBUTE:\n"
          + "        label:\n"
          + "          key2: value2\n";

  @Test
  void testConf7() throws Exception {
    InputStream is = new ByteArrayInputStream(CONF7.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config != null).isTrue();

    List<JmxRule> defs = config.getRules();
    assertThat(defs.size() == 1).isTrue();

    MetricDef metricDef = defs.get(0).buildMetricDef();
    assertThat(metricDef == null).isFalse();
    assertThat(metricDef.getMetricExtractors().length == 1).isTrue();

    MetricExtractor m1 = metricDef.getMetricExtractors()[0];
    AttributeValueExtractor a1 = m1.getMetricValueExtractor();
    assertThat("ATTRIBUTE".equals(a1.getAttributeName())).isTrue();
    assertThat(m1.getLabels().length == 2).isTrue();
    MetricBanner mb1 = m1.getBanner();
    assertThat("ATTRIBUTE".equals(mb1.getMetricName())).isTrue();
  }

  private static final String EMPTY_CONF = "---\n";

  @Test
  void testEmptyConf() throws Exception {
    InputStream is = new ByteArrayInputStream(EMPTY_CONF.getBytes(Charset.forName("UTF-8")));
    JmxConfig config = parser.loadConfig(is);
    assertThat(config == null).isTrue();
  }

  /*
   *     Negative tests
   */

  private static void runNegativeTest(String yaml) throws Exception {
    InputStream is = new ByteArrayInputStream(yaml.getBytes(Charset.forName("UTF-8")));

    Assertions.assertThrows(
        Exception.class,
        () -> {
          JmxConfig config = parser.loadConfig(is);
          assertThat(config != null).isTrue();

          List<JmxRule> defs = config.getRules();
          assertThat(defs.size() == 1).isTrue();
          defs.get(0).buildMetricDef();
        });
  }

  @Test
  void testNoBeans() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:                  # no bean\n"
            + "  - mapping:           # still no beans\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidObjectName() throws Exception {
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
  void testEmptyMapping() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n "
            + "rules:\n"
            + "  - bean: domain:type=6\n"
            + "    mapping:\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidAttributeName() throws Exception {
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
  void testInvalidTag() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        label:\n"
            + "          LABEL: attr(something)\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testInvalidType() throws Exception {
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
  void testInvalidTagFromAttribute() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        label:\n"
            + "          LABEL: attrib(.used)\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyTagFromAttribute() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        label:\n"
            + "          LABEL: attrib( )\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyTagFromParameter() throws Exception {
    String yaml =
        "---                                   # keep stupid spotlessJava at bay\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      ATTRIB:\n"
            + "        label:\n"
            + "          LABEL: param( )\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testEmptyPrefix() throws Exception {
    String yaml =
        "---\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    prefix:\n"
            + "    mapping:\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }

  @Test
  void testTypoInMetric() throws Exception {
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
  void testMessedUpSyntax() throws Exception {
    String yaml =
        "---\n"
            + "rules:\n"
            + "  - bean: domain:name=you\n"
            + "    mapping:\n"
            + "      label:     # not valid here\n"
            + "        key: value\n"
            + "      A:\n"
            + "        metric: METRIC_NAME\n";
    runNegativeTest(yaml);
  }
}
