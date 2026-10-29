plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")
  implementation("org.testcontainers:testcontainers")

  compileOnly("org.hibernate.reactive:hibernate-reactive-core:2.0.0.Final")
  compileOnly("io.vertx:vertx-sql-client:4.4.2")
  compileOnly("io.vertx:vertx-codegen:4.4.2")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
