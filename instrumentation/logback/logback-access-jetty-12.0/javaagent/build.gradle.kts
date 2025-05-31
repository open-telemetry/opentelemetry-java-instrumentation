plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-access")
    versions.set("[1.4.7,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("ch.qos.logback.access:jetty12") {
    version {
      strictly("2.0.0")
    }
  }
  compileOnly("ch.qos.logback.access:logback-access-common:2.0.4")
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.5.8")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-access:latest.release")
  } else {
    testImplementation("ch.qos.logback.access:jetty12") {
      version {
        strictly("2.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  compileOnly(project(":javaagent-bootstrap"))

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
}
