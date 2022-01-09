plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[0.9.16,)")
    skip("0.9.6") // has dependency on SNAPSHOT org.slf4j:slf4j-api:1.4.0-SNAPSHOT
    skip("0.8") // has dependency on non-existent org.slf4j:slf4j-api:1.1.0-RC0
    skip("0.6") // has dependency on pom only javax.jms:jms:1.1
    assertInverse.set(true)
  }
}

dependencies {
  library("ch.qos.logback:logback-classic:0.9.16")

  compileOnly(project(":instrumentation-api-appender"))

  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))

  latestDepTestLibrary("ch.qos.logback:logback-classic:1.2.+")

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.level=INFO")
}
