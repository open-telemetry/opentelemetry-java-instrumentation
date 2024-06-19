val stableVersion = "1.33.4"
val alphaVersion = "1.33.4-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
