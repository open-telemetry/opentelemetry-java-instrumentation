plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-data:spring-data-1.8:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-data:spring-data-common:testing"))

  testLibrary("org.hibernate.orm:hibernate-core:6.0.0.Final")
  testLibrary("org.springframework.data:spring-data-commons:3.0.0")
  testLibrary("org.springframework.data:spring-data-jpa:3.0.0")
  testLibrary("org.springframework:spring-test:6.0.0")

  testImplementation("org.hsqldb:hsqldb:2.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}
