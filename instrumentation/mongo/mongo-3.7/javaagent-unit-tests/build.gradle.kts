plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation(project(":instrumentation:mongo:mongo-3.1:library"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("org.mongodb:mongo-java-driver:3.11.0")
  testRuntimeOnly(project(":muzzle"))
}

tasks {
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*MongoClientInstrumentationModuleTest")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
