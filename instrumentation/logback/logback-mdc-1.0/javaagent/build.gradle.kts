plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[1.0.0,1.2.3]")
  }
}

testSets {
  create("addBaggageTest")
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

tasks {
  val addBaggageTest by existing(Test::class) {
    jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=true")
  }

  test {
    jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=false")
  }

  named("check") {
    dependsOn(addBaggageTest)
  }
}
