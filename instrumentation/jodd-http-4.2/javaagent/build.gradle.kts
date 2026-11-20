plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jodd")
    module.set("jodd-http")
    versions.set("[4.1.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  // 4.1.1 is the first version with HttpBase#headerOverwrite used by header injection
  library("org.jodd:jodd-http:4.1.1")

  testImplementation(project(":instrumentation-api-incubator"))
}

testing.suites {
  register<JvmTestSuite>("unitTests") {
    dependencies {
      implementation(project())
      implementation("org.jodd:jodd-http:4.2.0")
      implementation(project(":instrumentation-api"))
      implementation(project(":instrumentation-api-incubator"))
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}
