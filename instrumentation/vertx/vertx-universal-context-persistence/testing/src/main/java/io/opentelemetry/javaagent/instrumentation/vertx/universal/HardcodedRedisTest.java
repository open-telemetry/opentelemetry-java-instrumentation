/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple test to verify hardcoded Redis instrumentation is working.
 */
public final class HardcodedRedisTest {
  
  private HardcodedRedisTest() {}

  private static final Tracer tracer = GlobalOpenTelemetry.get().getTracer("test-tracer");

  public static void main(String[] args) throws InterruptedException {
    System.out.println("=== HARDCODED REDIS INSTRUMENTATION TEST ===");
    System.out.println("This test will verify that our hardcoded Redis instrumentation is working.");
    System.out.println("Look for these debug messages in the output:");
    System.out.println("  - UNIVERSAL-MODULE: Adding hardcoded Redis instrumentation for testing");
    System.out.println("  - HARDCODED-REDIS-TRANSFORM: ...");
    System.out.println("  - HARDCODED-REDIS-ENTER: ...");
    System.out.println("  - HARDCODED-REDIS-EXIT: ...");
    System.out.println();

    testHardcodedRedisInstrumentation();
  }

  private static void testHardcodedRedisInstrumentation() throws InterruptedException {
    System.out.println("--- Testing Hardcoded Redis Instrumentation ---");
    
    Vertx vertx = Vertx.vertx();
    
    // Create a span to provide context
    Span testSpan = tracer.spanBuilder("test-hardcoded-redis").startSpan();
    
    try (Scope scope = testSpan.makeCurrent()) {
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
      testSpan.end();
    }
    
    vertx.close();
    
    System.out.println("\n=== TEST SUMMARY ===");
    System.out.println("If you see the HARDCODED-REDIS debug messages above, the instrumentation is working!");
  }
}
