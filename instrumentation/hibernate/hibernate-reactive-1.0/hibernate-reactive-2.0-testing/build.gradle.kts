plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))
  implementation("org.testcontainers:testcontainers")

  compileOnly("org.hibernate.reactive:hibernate-reactive-core:2.0.0.Final")
  compileOnly("io.vertx:vertx-sql-client:4.4.2")
  compileOnly("io.vertx:vertx-codegen:4.4.2")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
