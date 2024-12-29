plugins {
  id("otel.javaagent-testing")

  id("io.quarkus") version "2.16.7.Final"
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:2.16.7.Final"))
  // fails with junit 5.11.+
  implementation(enforcedPlatform("org.junit:junit-bom:5.10.3"))
  implementation("io.quarkus:quarkus-resteasy-reactive")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:quarkus-resteasy-reactive:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))

  testImplementation(project(":instrumentation:quarkus-resteasy-reactive:common-testing"))
  testImplementation("io.quarkus:quarkus-junit5")
}

tasks.named("compileJava").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}
tasks.named("sourcesJar").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}
tasks.named("checkstyleTest").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}
tasks.named("compileTestJava").configure {
  dependsOn(tasks.named("compileQuarkusTestGeneratedSourcesJava"))
}
