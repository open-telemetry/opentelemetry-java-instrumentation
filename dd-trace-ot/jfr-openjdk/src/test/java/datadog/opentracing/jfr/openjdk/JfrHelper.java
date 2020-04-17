package datadog.opentracing.jfr.openjdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

public class JfrHelper {

  public static Object startRecording() {
    final Recording recording = new Recording();
    recording.start();
    return recording;
  }

  public static List<?> stopRecording(final Object object) throws IOException {
    final Recording recording = (Recording) object;
    final Path output = Files.createTempFile("recording", ".jfr");
    output.toFile().deleteOnExit();
    recording.dump(output);
    recording.stop();
    recording.close();

    return RecordingFile.readAllEvents(output);
  }
}
