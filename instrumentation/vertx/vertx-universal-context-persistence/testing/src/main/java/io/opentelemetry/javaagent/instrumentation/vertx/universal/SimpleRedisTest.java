/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple test to verify hardcoded Redis instrumentation is working.
 * This test will be run with the Java agent to see if our instrumentation works.
 */
public final class SimpleRedisTest {
  
  private SimpleRedisTest() {}

  public static void main(String[] args) throws InterruptedException {
    System.out.println("=== SIMPLE REDIS TEST WITH JAVA AGENT ===");
    System.out.println("This test should show our instrumentation debug messages if the agent is working.");
    System.out.println();

    testRedisWithAgent();
  }

  private static void testRedisWithAgent() throws InterruptedException {
    System.out.println("--- Testing Redis with Java Agent ---");
    
    Vertx vertx = Vertx.vertx();
    
    try {
      System.out.println("Creating Redis client...");
      
      // This should trigger our hardcoded Redis instrumentation when we call send()
      Redis redis = Redis.createClient(vertx, new RedisOptions().setConnectionString("redis://localhost:6379"));
      
      CountDownLatch latch = new CountDownLatch(1);
      
      redis.connect(connectionResult -> {
        if (connectionResult.succeeded()) {
          System.out.println("Redis connection succeeded");
          
          // This should trigger our HARDCODED-REDIS-ENTER and HARDCODED-REDIS-EXIT messages
          connectionResult.result().send(
              io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.PING), 
              sendResult -> {
                System.out.println("Redis PING completed");
                if (sendResult.succeeded()) {
                  System.out.println("✅ Redis PING successful: " + sendResult.result());
                } else {
                  System.out.println("❌ Redis PING failed: " + sendResult.cause().getMessage());
                }
                connectionResult.result().close();
                latch.countDown();
              });
        } else {
          System.out.println("❌ Redis connection failed (expected): " + connectionResult.cause().getMessage());
          latch.countDown();
        }
      });
      
      latch.await(10, TimeUnit.SECONDS);
    } finally {
      vertx.close();
    }
    
    System.out.println("\n=== TEST SUMMARY ===");
    System.out.println("If you see HARDCODED-REDIS debug messages above, the instrumentation is working!");
  }
}
