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
  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.5.8")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-classic:latest.release")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  compileOnly(project(":javaagent-bootstrap"))

  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-marker-attribute=true")
}
