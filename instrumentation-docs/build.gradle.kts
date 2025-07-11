plugins {
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.yaml:snakeyaml:2.4")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.1")
  implementation("io.opentelemetry:opentelemetry-sdk-common")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.13.3"))
  testImplementation("org.assertj:assertj-core:3.27.3")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
  val runAnalysis by registering(JavaExec::class) {
    dependsOn(classes)

    systemProperty("basePath", project.rootDir)
    mainClass.set("io.opentelemetry.instrumentation.docs.DocGeneratorApplication")
    classpath(sourceSets["main"].runtimeClasspath)
  }
}
