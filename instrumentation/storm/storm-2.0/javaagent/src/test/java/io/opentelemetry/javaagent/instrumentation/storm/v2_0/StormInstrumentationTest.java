/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Map;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class StormInstrumentationTest {

  private static final String DESTINATION = "default";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void shouldTraceSpoutEmitAndBoltExecute() throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new TestSpout(), 1);
    builder.setBolt("bolt", new TestBolt(), 1).localOrShuffleGrouping("spout");

    Config config = new Config();
    config.setNumWorkers(1);
    config.setDebug(false);

    LocalCluster cluster = new LocalCluster();
    cleanup.deferCleanup(cluster);

    cluster.submitTopology("storm-test", config, builder.createTopology());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasAttribute(equalTo(MESSAGING_SYSTEM, "storm")),
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasAttribute(equalTo(MESSAGING_SYSTEM, "storm"))
                        .hasParent(trace.getSpan(0))));
  }

  private static String producerSpanName() {
    return emitStableMessagingSemconv() ? "publish " + DESTINATION : DESTINATION + " publish";
  }

  private static String processSpanName() {
    return emitStableMessagingSemconv() ? "process " + DESTINATION : DESTINATION + " process";
  }

  public static class TestSpout extends BaseRichSpout {
    private static final long serialVersionUID = 1L;

    private SpoutOutputCollector collector;
    private boolean emitted;

    @Override
    public void open(
        Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
      this.collector = collector;
    }

    @Override
    public void nextTuple() {
      if (emitted) {
        return;
      }
      emitted = true;
      collector.emit(new Values("hello"));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("word"));
    }
  }

  public static class TestBolt extends BaseRichBolt {
    private static final long serialVersionUID = 1L;

    @Override
    public void prepare(
        Map<String, Object> conf, TopologyContext context, OutputCollector collector) {}

    @Override
    public void execute(Tuple input) {}

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {}
  }
}
