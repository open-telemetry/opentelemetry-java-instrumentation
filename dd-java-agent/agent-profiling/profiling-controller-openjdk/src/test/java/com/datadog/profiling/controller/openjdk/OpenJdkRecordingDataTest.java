package com.datadog.profiling.controller.openjdk;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import jdk.jfr.Recording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
// Proper unused stub detection doesn't work in junit5 yet,
// see https://github.com/mockito/mockito/issues/1540
@MockitoSettings(strictness = Strictness.LENIENT)
public class OpenJdkRecordingDataTest {

  private static final String TEST_NAME = "recording name";

  @Mock Instant start;
  @Mock Instant end;
  @Mock Instant customStart;
  @Mock Instant customEnd;
  @Mock private InputStream stream;
  @Mock private InputStream customStream;
  @Mock private Recording recording;

  private OpenJdkRecordingData recordingData;
  private OpenJdkRecordingData customRecordingData;

  @BeforeEach
  public void setup() throws IOException {
    when(recording.getStream(start, end)).thenReturn(stream);
    when(recording.getStream(customStart, customEnd)).thenReturn(customStream);
    when(recording.getStartTime()).thenReturn(start);
    when(recording.getStopTime()).thenReturn(end);
    when(recording.getName()).thenReturn(TEST_NAME);

    recordingData = new OpenJdkRecordingData(recording);
    customRecordingData = new OpenJdkRecordingData(recording, customStart, customEnd);
  }

  @Test
  public void testGetStream() throws IOException {
    assertEquals(stream, recordingData.getStream());
  }

  @Test
  public void testRelease() {
    recordingData.release();
    verify(recording).close();
  }

  @Test
  public void testGetName() {
    assertEquals(TEST_NAME, recordingData.getName());
  }

  @Test
  public void testToString() {
    assertThat(recordingData.toString(), containsString(TEST_NAME));
  }

  @Test
  public void testGetStart() {
    assertEquals(start, recordingData.getStart());
  }

  @Test
  public void testGetEnd() {
    assertEquals(end, recordingData.getEnd());
  }

  @Test
  public void testCustomGetStream() throws IOException {
    assertEquals(customStream, customRecordingData.getStream());
  }

  @Test
  public void testCustomGetStart() {
    assertEquals(customStart, customRecordingData.getStart());
  }

  @Test
  public void testCustomGetEnd() {
    assertEquals(customEnd, customRecordingData.getEnd());
  }

  @Test
  public void getRecording() {
    assertEquals(recording, recordingData.getRecording());
  }
}
