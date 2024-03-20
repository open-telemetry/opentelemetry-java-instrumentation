plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[2.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.xuxueli:xxl-job-core:2.3.0") {
    exclude("org.codehaus.groovy", "groovy")
  }
  implementation(project(":instrumentation:xxl-job:xxl-job-common:javaagent"))

  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.1.2:javaagent"))
  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.3.0:javaagent"))

  testImplementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.xxl-job.experimental-span-attributes=true")
}
