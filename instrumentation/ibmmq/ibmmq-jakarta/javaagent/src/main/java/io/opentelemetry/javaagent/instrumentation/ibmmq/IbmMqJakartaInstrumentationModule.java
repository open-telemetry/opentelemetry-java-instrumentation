/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Jakarta namespace counterpart of {@code IbmMqInstrumentationModule} (in the sibling {@code
 * instrumentation:ibmmq:javaagent} module), for applications on IBM's {@code
 * com.ibm.mq.jakarta.client} JMS provider.
 *
 * <p>Lives in its own Gradle module ({@code instrumentation:ibmmq:ibmmq-jakarta:javaagent}) rather
 * than as more {@code TypeInstrumentation}s registered on the javax module, for two independent
 * reasons: muzzle collects one reference set per module shared across every {@code
 * TypeInstrumentation} it registers, and javax/jakarta MQ clients are disjoint jars whose types can
 * never both resolve on one application's classpath -- so mixing them in one module's reference set
 * would fail muzzle validation for every application on either client. Separately, and just as
 * decisively: co-locating both {@code InstrumentationModule}s in one compiled jar means both are
 * always loaded together in any test run against that jar, so whichever one's classpath a given
 * test does <em>not</em> set up correctly logs an (expected, correct) muzzle mismatch that OTel's
 * {@code AgentTestRunner} treats as a hard test failure regardless of whether that module was ever
 * supposed to apply -- confirmed empirically, not just by analogy to muzzle's per-module
 * reference-collection mechanics. Genuinely separate Gradle modules avoid both problems: each
 * module's own compiled output, and therefore its own test agent, never includes the sibling
 * module's class at all.
 *
 * <p>The namespace-agnostic logic both this and the javax module rely on lives in a third module,
 * {@code instrumentation:ibmmq:ibmmq-common:javaagent} ({@link IbmMqQmidSupport}), which imports no
 * MQ or JMS type at all -- see that class's javadoc.
 *
 * <p>Shares the primary instrumentation name {@code "ibmmq"} with the javax module so that {@code
 * otel.instrumentation.ibmmq.*} configuration, including the experimental span attribute opt-in,
 * governs both namespaces uniformly.
 */
@AutoService(InstrumentationModule.class)
public class IbmMqJakartaInstrumentationModule extends InstrumentationModule {

  public IbmMqJakartaInstrumentationModule() {
    super("ibmmq", "ibm-mq-jakarta");
  }

  @Override
  public int order() {
    // Apply after the generic JMS instrumentation (jms-3.0, for jakarta) so that its span is the
    // one being enriched -- same reasoning and same value as IbmMqInstrumentationModule.
    return 1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new IbmMqJakartaJmsProducerInstrumentation(),
        // async MessageListener consumers: capture at registration, stamp at delivery
        new IbmMqJakartaJmsSetListenerInstrumentation(),
        new IbmMqJakartaJmsListenerInstrumentation(),
        // message-keyed capture: carries the QMID from receive() to whichever later span
        // processes the same Message, for containers that never call setMessageListener at all
        // (e.g. Spring's default JmsListenerContainerFactory) -- see IbmMqJakartaJmsListenerQmid.
        new IbmMqJakartaJmsReceiveInstrumentation());
  }
}
