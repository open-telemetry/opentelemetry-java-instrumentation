/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
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

@SuppressWarnings("deprecation") // using deprecated semconv
class StormInstrumentationTest {

  private static final String DESTINATION = "default";
  private static final AttributeKey<String> MESSAGING_CLIENT_ID_OLD =
      AttributeKey.stringKey("messaging.client_id");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void shouldTraceSpoutEmitAndBoltExecute() throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new TestSpout(), 1);
    builder.setBolt("bolt", new TestBolt(), 1).localOrShuffleGrouping("spout");

    submitTopology(builder);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(producerAttributes()),
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(processLinks(trace.getSpan(0)))
                        .hasAttributesSatisfyingExactly(processAttributes("spout"))));
  }

  @Test
  void shouldTraceBoltEmit() throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new TestSpout(), 1);
    builder.setBolt("bolt1", new EmittingBolt(), 1).localOrShuffleGrouping("spout");
    builder.setBolt("bolt2", new TestBolt(), 1).localOrShuffleGrouping("bolt1");

    submitTopology(builder);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                // spout emit
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(producerAttributes()),
                // bolt1 process
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(processLinks(trace.getSpan(0)))
                        .hasAttributesSatisfyingExactly(processAttributes("spout")),
                // bolt1 emit
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(producerAttributes()),
                // bolt2 process
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(2))
                        .hasLinks(processLinks(trace.getSpan(2)))
                        .hasAttributesSatisfyingExactly(processAttributes("bolt1"))));
  }

  @Test
  void shouldTraceDirectEmits() throws Exception {
    TopologyBuilder builder = new TopologyBuilder();
    builder.setSpout("spout", new DirectSpout("bolt1"), 1);
    builder.setBolt("bolt1", new DirectEmittingBolt("bolt2"), 1).directGrouping("spout");
    builder.setBolt("bolt2", new TestBolt(), 1).directGrouping("bolt1");

    submitTopology(builder);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                // spout emitDirect
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(producerAttributes()),
                // bolt1 process
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(processLinks(trace.getSpan(0)))
                        .hasAttributesSatisfyingExactly(processAttributes("spout")),
                // bolt1 emitDirect
                span ->
                    span.hasName(producerSpanName())
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(producerAttributes()),
                // bolt2 process
                span ->
                    span.hasName(processSpanName())
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(2))
                        .hasLinks(processLinks(trace.getSpan(2)))
                        .hasAttributesSatisfyingExactly(processAttributes("bolt1"))));
  }

  private void submitTopology(TopologyBuilder builder) throws Exception {
    Config config = new Config();
    config.setNumWorkers(1);
    config.setDebug(false);

    LocalCluster cluster = new LocalCluster();
    cleanup.deferCleanup(cluster);

    cluster.submitTopology("storm-test", config, builder.createTopology());
  }

  private static List<AttributeAssertion> producerAttributes() {
    return asList(
        equalTo(MESSAGING_SYSTEM, "storm"),
        equalTo(MESSAGING_DESTINATION_NAME, DESTINATION),
        equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
        equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "publish" : null),
        equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null));
  }

  private static List<AttributeAssertion> processAttributes(String sourceComponent) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "storm"),
                equalTo(MESSAGING_DESTINATION_NAME, DESTINATION),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
                equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
                // unanchored Storm tuples carry an empty MessageId
                equalTo(MESSAGING_MESSAGE_ID, "{}")));
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_CLIENT_ID_OLD, sourceComponent));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_CLIENT_ID, sourceComponent));
    }
    return assertions;
  }

  private static LinkData[] processLinks(SpanData producerSpan) {
    // the stable messaging semconv links every processed message to its creation context, which is
    // also the process span's parent
    return emitStableMessagingSemconv()
        ? new LinkData[] {LinkData.create(producerSpan.getSpanContext())}
        : new LinkData[0];
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

  public static class DirectSpout extends BaseRichSpout {
    private static final long serialVersionUID = 1L;

    private final String targetComponent;

    private SpoutOutputCollector collector;
    private int targetTaskId = -1;
    private boolean emitted;

    DirectSpout(String targetComponent) {
      this.targetComponent = targetComponent;
    }

    @Override
    public void open(
        Map<String, Object> conf, TopologyContext context, SpoutOutputCollector collector) {
      this.collector = collector;
      this.targetTaskId = context.getComponentTasks(targetComponent).get(0);
    }

    @Override
    public void nextTuple() {
      if (emitted) {
        return;
      }
      emitted = true;
      collector.emitDirect(targetTaskId, new Values("hello"));
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

  public static class EmittingBolt extends BaseRichBolt {
    private static final long serialVersionUID = 1L;

    private transient OutputCollector collector;

    @Override
    public void prepare(
        Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
      this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
      collector.emit(new Values("hello"));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("word"));
    }
  }

  public static class DirectEmittingBolt extends BaseRichBolt {
    private static final long serialVersionUID = 1L;

    private final String targetComponent;

    private transient OutputCollector collector;
    private int targetTaskId = -1;

    DirectEmittingBolt(String targetComponent) {
      this.targetComponent = targetComponent;
    }

    @Override
    public void prepare(
        Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
      this.collector = collector;
      this.targetTaskId = context.getComponentTasks(targetComponent).get(0);
    }

    @Override
    public void execute(Tuple input) {
      collector.emitDirect(targetTaskId, new Values("hello"));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
      declarer.declare(new Fields("word"));
    }
  }
}
