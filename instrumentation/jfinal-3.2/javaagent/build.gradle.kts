plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.jfinal")
    module.set("jfinal")
    versions.set("[3.2,)")
    assertInverse.set(true)
  }
}



if (!(findProperty("testLatestDeps") as Boolean)) {
  otelJava {
  //jfinal 3.6 doesn't work with Java 9+
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
  }
}

dependencies {
  library("com.jfinal:jfinal:3.6")
  testLibrary("com.jfinal:jetty-server:2019.3")
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-common:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
