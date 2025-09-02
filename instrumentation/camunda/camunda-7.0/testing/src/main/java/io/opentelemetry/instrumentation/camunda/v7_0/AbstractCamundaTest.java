/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.camunda.v7_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractCamundaTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private ProcessEngine processEngine;
  private RuntimeService runtimeService;
  private HistoryService historyService;

  @BeforeEach
  void setUp() {
    processEngine =
        ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
            .buildProcessEngine();
    runtimeService = processEngine.getRuntimeService();
    historyService = processEngine.getHistoryService();

    Product mockDelegate = new Product();

    Mocks.register("Product", mockDelegate);
  }

  @AfterEach
  void tearDown() {
    processEngine.close();
  }

  @Test
  void testProcessExecutionAllSuccess() {
    Deployment deployment =
        processEngine
            .getRepositoryService()
            .createDeployment()
            .addClasspathResource("testMainProcess.bpmn")
            .addClasspathResource("customerSubProcess.bpmn")
            .deploy();

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testMainProcess");

    assertNotNull(processInstance);

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testMainProcess")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camunda.processdefinitionkey"), "testMainProcess"))
                        .hasNoParent(),
                span ->
                    span.hasName("Get Product Info Task")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camunda.activityid"), "getProductInfo"),
                            satisfies(
                                stringKey("camunda.processdefinitionid"),
                                value -> value.startsWith("testMainProcess")),
                            satisfies(
                                stringKey("camunda.processinstanceid"),
                                value -> assertNotNull(value)),
                            equalTo(stringKey("camunda.activityname"), "Get Product Info")),
                span ->
                    span.hasName("Verify Customer Task")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camunda.activityid"), "verifyCustomer"),
                            satisfies(
                                stringKey("camunda.processdefinitionid"),
                                value -> value.startsWith("testMainProcess")),
                            satisfies(
                                stringKey("camunda.processinstanceid"),
                                value -> assertNotNull(value)),
                            equalTo(stringKey("camunda.activityname"), "Verify Customer")),
                span ->
                    span.hasName("End Event")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("camunda.processdefinitionid"),
                                value -> value.startsWith("customerSubProcess")),
                            equalTo(stringKey("camunda.activityid"), "Event_12mjag6"),
                            satisfies(
                                stringKey("camunda.processinstanceid"),
                                value -> assertNotNull(value)),
                            equalTo(stringKey("camunda.activityname"), "End")),
                span ->
                    span.hasName("Send Product Info Task")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camunda.activityid"), "sendProductInfo"),
                            satisfies(
                                stringKey("camunda.processdefinitionid"),
                                value -> value.startsWith("testMainProcess")),
                            satisfies(
                                stringKey("camunda.processinstanceid"),
                                value -> assertNotNull(value)),
                            equalTo(stringKey("camunda.activityname"), "Send Product Info")),
                span ->
                    span.hasName("End Event")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("camunda.processdefinitionid"),
                                value -> value.startsWith("testMainProcess")),
                            equalTo(stringKey("camunda.activityid"), "Event_110t6od"),
                            satisfies(
                                stringKey("camunda.processinstanceid"),
                                value -> assertNotNull(value)),
                            equalTo(stringKey("camunda.activityname"), "End"))));

    historyService.deleteHistoricProcessInstanceIfExists(processInstance.getId());
    processEngine.getRepositoryService().deleteDeployment(deployment.getId(), true);
  }
}
