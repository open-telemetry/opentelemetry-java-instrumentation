/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("otel.java-conventions")
}

dependencies {
  // === CORE DEPENDENCIES ===
  api(project(":testing-common"))
  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-extension-api"))
  implementation(project(":javaagent-tooling"))
  
  // === VERTX DEPENDENCIES ===
  implementation("io.vertx:vertx-core:3.9.0")
  implementation("io.vertx:vertx-codegen:3.9.0")
  implementation("io.vertx:vertx-web:3.9.0")
  implementation("io.vertx:vertx-redis-client:3.9.0")
}

tasks.test {
  useJUnitPlatform()
  
  // Enable debug output for our tests
  systemProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
  systemProperty("otel.javaagent.debug", "true")
}

tasks.register<JavaExec>("runHardcodedRedisTest") {
  group = "verification"
  description = "Run the Hardcoded Redis test manually"
  
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("io.opentelemetry.javaagent.instrumentation.vertx.universal.HardcodedRedisTest")
  
  // Enable debug output
  systemProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
  systemProperty("otel.javaagent.debug", "true")
}

tasks.register<JavaExec>("runSimpleRedisTest") {
  group = "verification"
  description = "Run the Simple Redis test with Java agent"
  
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("io.opentelemetry.javaagent.instrumentation.vertx.universal.SimpleRedisTest")
  
  // Enable debug output
  systemProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
  systemProperty("otel.javaagent.debug", "true")
}
