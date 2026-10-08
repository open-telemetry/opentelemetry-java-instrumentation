plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat.embed")
    module.set("tomcat-embed-core")
    versions.set("[10,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:tomcat:tomcat-common-7.0:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")

  latestDepTestLibrary("org.apache.tomcat:jakartaee-migration:latest.release")

  // Make sure nothing breaks due to both 7.0 and 10.0 modules being present together
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  // testing whether instrumentation still works when javax servlet api is also present
  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.request-parameters.included=test-*")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testUserNameCapture = register<Test>("testUserNameCapture") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*TomcatHandlerTest.capturesUserNameFromPrincipal")
    }
    jvmArgs("-Dotel.instrumentation.common.user.name.enabled=true")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.servlet.experimental.request-parameters.included=test-*")
    systemProperty("metadataConfig", "otel.instrumentation.common.user.name.enabled=true,otel.instrumentation.servlet.experimental.request-parameters.included=test-*")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled=true")
  }

  check {
    dependsOn(testExperimental)
    dependsOn(testUserNameCapture)
  }
}

// Tomcat 10 uses deprecation annotation methods `forRemoval()` and `since()`
// in jakarta.servlet.http.HttpServlet that don't work with Java 8
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
