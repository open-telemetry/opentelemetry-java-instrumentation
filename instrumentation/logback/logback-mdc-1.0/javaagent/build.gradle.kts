plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[1.0.0,1.2.3]")
  }
}

dependencies {
  implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))

  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.6.4")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-classic:+")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.6.4")
      }
    }
  }

  testImplementation(project(":instrumentation:logback:logback-mdc-1.0:testing"))
}
