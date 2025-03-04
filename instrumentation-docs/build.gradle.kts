plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  testImplementation(enforcedPlatform("org.junit:junit-bom:5.12.0"))
  testImplementation("org.assertj:assertj-core:3.27.3")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
  val generateDocs by registering(JavaExec::class) {
    dependsOn(classes)

    mainClass.set("io.opentelemetry.instrumentation.docs.DocGeneratorApplication")
    classpath(sourceSets["main"].runtimeClasspath)
  }
}
