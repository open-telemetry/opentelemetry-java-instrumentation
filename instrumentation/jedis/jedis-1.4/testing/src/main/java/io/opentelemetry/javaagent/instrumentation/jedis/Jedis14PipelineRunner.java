/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis;

import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPipeline;

class Jedis14PipelineRunner {

  static void run(Jedis jedis, AbstractJedisTest.PipelineScenario scenario) {
    jedis.pipelined(
        new JedisPipeline() {
          @Override
          public void execute() {
            scenario.run(new ClientPipelineOperations(client));
          }
        });
  }

  private static class ClientPipelineOperations implements AbstractJedisTest.PipelineOperations {
    private final Client client;

    private ClientPipelineOperations(Client client) {
      this.client = client;
    }

    @Override
    public void set(String key, String value) {
      client.set(key, value);
    }

    @Override
    public void get(String key) {
      client.get(key);
    }
  }

  private Jedis14PipelineRunner() {}
}
