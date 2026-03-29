plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-client")
    versions.set("[12,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:library"))

  library("org.eclipse.jetty:jetty-client:12.0.0")

  testInstrumentation(project(":instrumentation:jetty-httpclient:jetty-httpclient-9.2:javaagent"))

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata"))
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
