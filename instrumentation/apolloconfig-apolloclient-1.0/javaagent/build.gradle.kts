plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ctrip.framework.apollo")
    module.set("apollo-client")
    versions.set("[1.0.0,2.3.0]")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("com.ctrip.framework.apollo:apollo-client:1.1.0")

  testImplementation(project(":testing-common"))

  latestDepTestLibrary("com.ctrip.framework.apollo:apollo-client:1.1.+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
