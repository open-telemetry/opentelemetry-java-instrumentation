plugins {
  id("otel.javaagent-testing")

  id("io.quarkus") version "3.0.0.Final"
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

// io.quarkus.platform:quarkus-bom is missing for 3.0.0.Final
var quarkusVersion = "3.0.1.Final"
if (findProperty("testLatestDeps") as Boolean) {
  quarkusVersion = "3.5.+"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
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
