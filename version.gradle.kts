val stableVersion = "1.17.1-SNAPSHOT"
val alphaVersion = "1.17.1-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
