package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringJpaTest extends AgentInstrumentationSpecification {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();




}
