plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.cloud")
    module.set("spring-cloud-starter-gateway-server-webmvc")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc:5.0.0")

  implementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:javaagent"))

  testInstrumentation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:testing"))

  testLibrary("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc:5.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-test:4.0.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.spring-cloud-gateway.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}

// spring 7 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
