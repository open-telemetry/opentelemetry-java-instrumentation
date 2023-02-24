plugins {
  id("otel.library-instrumentation")
  id("org.unbroken-dome.test-sets")
}

testSets {
  create("addBaggageTest")
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
  val addBaggageTest by existing

  named("check") {
    dependsOn(addBaggageTest)
  }
}
