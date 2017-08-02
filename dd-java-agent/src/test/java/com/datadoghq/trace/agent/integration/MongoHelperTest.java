package com.datadoghq.trace.agent.integration;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.datadoghq.trace.DDSpan;
import com.datadoghq.trace.DDTracer;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonDocument;
import org.junit.Test;

public class MongoHelperTest {

  @Test
  public void test() {

    final CommandStartedEvent cmd =
        new CommandStartedEvent(1, null, "databasename", "query", new BsonDocument());

    final DDSpan span = new DDTracer().buildSpan("foo").startManual();
    new MongoHelper(null).decorate(span, cmd);

    assertThat(span.context().getSpanType()).isEqualTo("mongodb");
    assertThat(span.context().getResourceName())
        .isEqualTo(span.context().getTags().get("db.statement"));
  }
}
