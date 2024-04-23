val stableVersion = "2.4.0-SNAPSHOT"
val alphaVersion = "2.4.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
