import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.champeau.jmh.JMHTask
import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.jmh-conventions")
}

dependencies {
  jmhImplementation("org.springframework.boot:spring-boot-starter-web:2.5.2")

  testImplementation("org.assertj:assertj-core")
}

tasks {

  // TODO(trask) without disabling errorprone, jmh task fails with
  //  Task :testing-overhead-jmh:jmhCompileGeneratedClasses FAILED
  //   error: plug-in not found: ErrorProne
  withType<JavaCompile>().configureEach {
    options.errorprone {
      isEnabled.set(false)
    }
  }

  // TODO(trask) move to otel.jmh-conventions?
  val jmhFork = gradle.startParameter.projectProperties["jmh.fork"]?.toInt()
  val jmhIncludes = gradle.startParameter.projectProperties["jmh.includes"]

  // this option should point to the benchmark that you want to run with JFR
  val jmhFlightRecording = gradle.startParameter.projectProperties.get("jmh.flightRecording")

  named<JMHTask>("jmh") {
    val shadowTask = project(":javaagent").tasks.named<ShadowJar>("shadowJar").get()
    inputs.files(layout.files(shadowTask))

    // note: without an exporter, toSpanData() won't even be called
    // (which is good for benchmarking the instrumentation itself)
    val args = mutableListOf(
      "-javaagent:${shadowTask.archiveFile.get()}",
      "-Dotel.traces.exporter=none",
      "-Dotel.metrics.exporter=none",
      // avoid instrumenting HttpURLConnection for now since it is used to make the requests
      // and this benchmark is focused on servlet overhead for now
      "-Dotel.instrumentation.http-url-connection.enabled=false"
    )
    if (jmhFlightRecording != null) {
      args.addAll(listOf(
        "-XX:+FlightRecorder",
        "-XX:StartFlightRecording=settings=profile.jfc,delay=50s,duration=300s,filename=recording.jfr",
        "-XX:FlightRecorderOptions=stackdepth=1000",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
      ))

      includes.add(jmhFlightRecording)

      fork.set(1)
      iterations.set(30)
    }
    // see https://github.com/melix/jmh-gradle-plugin/issues/200
    jvmArgsPrepend.add(args.joinToString(" "))

    if (jmhIncludes != null) {
      includes.addAll(jmhIncludes.split(','))
    }
    if (jmhFork != null) {
      fork.set(jmhFork)
    }

    // TODO(trask) is this ok? if it's ok, move to otel.jmh-conventions?
    outputs.upToDateWhen { false }
  }
}
