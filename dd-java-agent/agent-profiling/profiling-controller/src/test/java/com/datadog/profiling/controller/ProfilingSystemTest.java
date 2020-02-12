/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadog.profiling.controller;

import static com.datadog.profiling.controller.RecordingType.CONTINUOUS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
// Proper unused stub detection doesn't work in junit5 yet,
// see https://github.com/mockito/mockito/issues/1540
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProfilingSystemTest {

  // Time in milliseconds when all things should have been done by
  // Should be noticeably bigger than one recording iteration
  private static final long REASONABLE_TIMEOUT = 5000;

  private final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);

  @Mock private ThreadLocalRandom threadLocalRandom;
  @Mock private Controller controller;
  @Mock private OngoingRecording recording;
  @Mock private RecordingData recordingData;
  @Mock private RecordingDataListener listener;

  @BeforeEach
  public void setup() {
    when(controller.createRecording(ProfilingSystem.RECORDING_NAME)).thenReturn(recording);
    when(threadLocalRandom.nextInt(eq(1), anyInt())).thenReturn(1);
  }

  @AfterEach
  public void tearDown() {
    pool.shutdown();
  }

  @Test
  public void testShutdown() throws ConfigurationException {
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ZERO,
            Duration.ofMillis(300),
            pool,
            threadLocalRandom);
    startProfilingSystem(system);
    verify(controller).createRecording(any());
    system.shutdown();

    verify(recording).close();
    assertTrue(pool.isTerminated());
  }

  @Test
  public void testShutdownWithRunningProfilingRecording() throws ConfigurationException {
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ZERO,
            Duration.ofMillis(300),
            pool,
            threadLocalRandom);
    startProfilingSystem(system);
    verify(controller).createRecording(any());
    system.shutdown();

    verify(recording).close();
    assertTrue(pool.isTerminated());
  }

  @Test
  public void testShutdownInterruption() throws ConfigurationException {
    final Thread mainThread = Thread.currentThread();
    doAnswer(
            (InvocationOnMock invocation) -> {
              while (!pool.isShutdown()) {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  // Ignore InterruptedException to make sure this threads lives through executor
                  // shutdown
                }
              }
              // Interrupting main thread to make sure this is handled properly
              mainThread.interrupt();
              return null;
            })
        .when(listener)
        .onNewData(any(), any());
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            Duration.ofMillis(100),
            pool,
            threadLocalRandom);
    startProfilingSystem(system);
    // Make sure we actually started the recording before terminating
    verify(controller, timeout(300)).createRecording(any());
    system.shutdown();
    assertTrue(true, "Shutdown exited cleanly after interruption");
  }

  @Test
  public void testCanShutDownWithoutStarting() throws ConfigurationException {
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            Duration.ofMillis(300),
            pool,
            threadLocalRandom);
    system.shutdown();
    assertTrue(pool.isTerminated());
  }

  @Test
  public void testDoesntSendDataIfNotStarted() throws InterruptedException, ConfigurationException {
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            Duration.ofMillis(1));
    Thread.sleep(50);
    system.shutdown();
    verify(controller, never()).createRecording(any());
    verify(listener, never()).onNewData(any(), any());
  }

  @Test
  public void testDoesntSendPeriodicRecordingIfPeriodicRecordingIsDisabled()
      throws InterruptedException, ConfigurationException {
    when(recording.snapshot(any(), any())).thenReturn(recordingData);
    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            Duration.ofMillis(10));
    startProfilingSystem(system);
    Thread.sleep(200);
    system.shutdown();
    verify(listener, atLeastOnce()).onNewData(CONTINUOUS, recordingData);
  }

  @Test
  public void testProfilingSystemNegativeStartupDelay() {
    assertThrows(
        ConfigurationException.class,
        () -> {
          new ProfilingSystem(
              controller, listener, Duration.ofMillis(-10), Duration.ZERO, Duration.ofMillis(200));
        });
  }

  @Test
  public void testProfilingSystemNegativeStartupRandomRangeDelay() {
    assertThrows(
        ConfigurationException.class,
        () -> {
          new ProfilingSystem(
              controller,
              listener,
              Duration.ofMillis(10),
              Duration.ofMillis(-20),
              Duration.ofMillis(200));
        });
  }

  @Test
  public void testProfilingSystemNegativeUploadPeriod() {
    assertThrows(
        ConfigurationException.class,
        () -> {
          new ProfilingSystem(
              controller,
              listener,
              Duration.ofMillis(10),
              Duration.ofMillis(20),
              Duration.ofMillis(-200));
        });
  }

  /** Ensure that we continue recording after one recording fails to get created */
  @Test
  public void testRecordingSnapshotError() throws ConfigurationException {
    final Duration uploadPeriod = Duration.ofMillis(300);
    final List<RecordingData> generatedRecordingData = new ArrayList<>();
    when(recording.snapshot(any(), any()))
        .thenThrow(new RuntimeException("Test"))
        .thenAnswer(generateMockRecordingData(generatedRecordingData));

    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            uploadPeriod,
            pool,
            threadLocalRandom);
    startProfilingSystem(system);

    final ArgumentCaptor<RecordingData> captor = ArgumentCaptor.forClass(RecordingData.class);
    verify(listener, timeout(REASONABLE_TIMEOUT).times(2))
        .onNewData(eq(CONTINUOUS), captor.capture());
    assertEquals(generatedRecordingData, captor.getAllValues());

    system.shutdown();
  }

  @Test
  public void testRecordingSnapshotNoData() throws ConfigurationException {
    final Duration uploadPeriod = Duration.ofMillis(300);
    final List<RecordingData> generatedRecordingData = new ArrayList<>();
    when(recording.snapshot(any(), any()))
        .thenReturn(null)
        .thenAnswer(generateMockRecordingData(generatedRecordingData));

    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            Duration.ofMillis(10),
            Duration.ofMillis(5),
            uploadPeriod,
            pool,
            threadLocalRandom);
    startProfilingSystem(system);

    final ArgumentCaptor<RecordingData> captor = ArgumentCaptor.forClass(RecordingData.class);
    verify(listener, timeout(REASONABLE_TIMEOUT).times(2))
        .onNewData(eq(CONTINUOUS), captor.capture());
    assertEquals(generatedRecordingData, captor.getAllValues());

    system.shutdown();
  }

  @Test
  public void testRandomizedStartupDelay() throws ConfigurationException {
    final Duration startupDelay = Duration.ofMillis(100);
    final Duration startupDelayRandomRange = Duration.ofMillis(500);
    final Duration additionalRandomDelay = Duration.ofMillis(300);

    when(threadLocalRandom.nextLong(startupDelayRandomRange.toMillis()))
        .thenReturn(additionalRandomDelay.toMillis());

    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            startupDelay,
            startupDelayRandomRange,
            Duration.ofMillis(100),
            pool,
            threadLocalRandom);

    final Duration randomizedDelay = system.getStartupDelay();

    assertEquals(startupDelay.plus(additionalRandomDelay), randomizedDelay);
  }

  @Test
  public void testFixedStartupDelay() throws ConfigurationException {
    final Duration startupDelay = Duration.ofMillis(100);

    final ProfilingSystem system =
        new ProfilingSystem(
            controller,
            listener,
            startupDelay,
            Duration.ZERO,
            Duration.ofMillis(100),
            pool,
            threadLocalRandom);

    assertEquals(startupDelay, system.getStartupDelay());
  }

  private Answer<Object> generateMockRecordingData(
      final List<RecordingData> generatedRecordingData) {
    return (InvocationOnMock invocation) -> {
      final RecordingData recordingData = mock(RecordingData.class);
      when(recordingData.getStart()).thenReturn(invocation.getArgument(0, Instant.class));
      when(recordingData.getEnd()).thenReturn(invocation.getArgument(1, Instant.class));
      generatedRecordingData.add(recordingData);
      return recordingData;
    };
  }

  private void startProfilingSystem(final ProfilingSystem system) {
    system.start();
    await().until(system::isStarted);
  }
}
