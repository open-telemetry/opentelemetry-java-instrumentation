package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlSession;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.testing.cassandra.v4_4.AbstractCassandra44Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraTest extends AbstractCassandra44Test {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected CqlSession wrap(CqlSession session) {
    return CassandraTelemetry.create(testing.getOpenTelemetry()).wrap(session);
  }
}
