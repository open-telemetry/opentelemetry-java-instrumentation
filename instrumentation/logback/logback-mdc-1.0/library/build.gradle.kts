plugins {
  id("otel.library-instrumentation")
}

testing {
  suites {
    val addBaggageTest by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=true")
          }
        }
      }
    }
  }
}

configurations {
  named("addBaggageTestImplementation") {
    extendsFrom(configurations["testImplementation"])
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

tasks {
  named("check") {
    dependsOn(testing.suites)
  }
}
