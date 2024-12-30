plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[1.9.2, 2.1.2)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.xuxueli:xxl-job-core:1.9.2") {
    exclude("org.codehaus.groovy", "groovy")
  }
  implementation(project(":instrumentation:xxl-job:xxl-job-common:javaagent"))

  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.1.2:javaagent"))
  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.3.0:javaagent"))

  // It needs the javax.annotation-api in xxl-job-core 1.9.2.
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))
  latestDepTestLibrary("com.xuxueli:xxl-job-core:2.1.1") { // see xxl-job-2.1.2 module
    exclude("org.codehaus.groovy", "groovy")
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.xxl-job.experimental-span-attributes=true")
}
