plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat.embed")
    module.set("tomcat-embed-core")
    versions.set("[10,)")
  }
}

dependencies {
  implementation(project(":instrumentation:tomcat:tomcat-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")

  // Make sure nothing breaks due to both 7.0 and 10.0 modules being present together
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
}
