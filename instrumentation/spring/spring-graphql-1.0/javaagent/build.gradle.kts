plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-graphql")
    versions.set("(,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.graphql:spring-graphql:1.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.boot:spring-boot-starter-web:2.7.0")
  testLibrary("org.springframework.boot:spring-boot-starter-webflux:2.7.0")
  testLibrary("org.springframework.graphql:spring-graphql-test:1.0.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
