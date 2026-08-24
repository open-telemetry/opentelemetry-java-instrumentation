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

@AutoService(InstrumentationModule.class)
public class IbmMqInstrumentationModule extends InstrumentationModule {

  public IbmMqInstrumentationModule() {
    super("ibmmq", "ibm-mq");
  }

  @Override
  public int order() {
    // Apply after the generic JMS instrumentation so that its span is the one being enriched.
    return 1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        // classic com.ibm.mq base API (owns its own spans)
        new IbmMqConnectionInstrumentation(),
        new IbmMqProducerInstrumentation(),
        // IBM MQ JMS provider, javax.jms namespace (com.ibm.mq.allclient): additive enrichment of
        // the generic JMS spans. See IbmMqJakartaInstrumentationModule for the jakarta.jms
        // namespace counterpart (com.ibm.mq.jakarta.client) -- kept as a fully separate
        // InstrumentationModule, never registered here, because muzzle collects one reference set
        // per module and the two MQ clients are disjoint jars whose types can never both resolve
        // on one application's classpath.
        new IbmMqJmsProducerInstrumentation(),
        // async MessageListener consumers: capture at registration, stamp at delivery
        new IbmMqJmsSetListenerInstrumentation(),
        new IbmMqJmsListenerInstrumentation(),
        // message-keyed capture: carries the QMID from receive() to whichever later span
        // processes the same Message, for containers that never call setMessageListener at all
        // (e.g. Spring's default JmsListenerContainerFactory) -- see IbmMqJmsListenerQmid.
        new IbmMqJmsReceiveInstrumentation());
    // NOTE on synchronous MessageConsumer.receive(): the generic JMS instrumentation creates that
    // span in its own exit advice via startAndEnd (created and ended in one call, never made
    // current), so no advice here can enrich THAT span -- writing to Span.current() there would
    // silently stamp whatever unrelated span happened to be active, which is why this module does
    // not attempt it. IbmMqJmsReceiveInstrumentation DOES instrument receive()/receiveNoWait(), but
    // only to remember the QMID against the returned Message for a *later* span to pick up (see
    // IbmMqJmsListenerQmid) -- it never creates, ends, or writes an attribute onto the receive
    // call's own span.
  }
}
