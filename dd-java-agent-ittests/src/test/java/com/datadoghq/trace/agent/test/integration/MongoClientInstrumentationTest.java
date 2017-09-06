package com.datadoghq.trace.agent.test.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClient;
import org.junit.Test;

public class MongoClientInstrumentationTest {

  @Test
  public void test() {
    MongoClient mongoClient = new MongoClient();

    assertThat(mongoClient.getMongoClientOptions().getCommandListeners().size()).isEqualTo(1);
    assertThat(
            mongoClient
                .getMongoClientOptions()
                .getCommandListeners()
                .get(0)
                .getClass()
                .getSimpleName())
        .isEqualTo("TracingCommandListener");

    mongoClient.close();
  }
}
