plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[6.0.0,)")
    // these versions depend on javax.faces:jsf-api:1.1 which was released as pom only
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework:spring-web:6.0.0")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.http.client.emit-experimental-telemetry=true")
  systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
}
