plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-context")
    versions.set("[3.1.0.RELEASE,]")
    assertInverse.set(true)
  }
}

dependencies {
  // 3.2.3 is the first version with which the tests will run. Lower versions require other
  // classes and packages to be imported. Versions 3.1.0+ work with the instrumentation.
  library("org.springframework:spring-context:3.1.0.RELEASE")
  testLibrary("org.springframework:spring-context:3.2.3.RELEASE")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
