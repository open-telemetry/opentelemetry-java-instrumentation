plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:sofa-rpc-5.4:library"))

  library("com.alipay.sofa:sofa-rpc-all:5.4.0")

  testImplementation(project(":instrumentation:sofa-rpc-5.4:testing"))
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

tasks {
  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=rpc")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=rpc/dup")
  }

  check {
    dependsOn(testPreviewSemconv, testBothSemconv)
  }
}
