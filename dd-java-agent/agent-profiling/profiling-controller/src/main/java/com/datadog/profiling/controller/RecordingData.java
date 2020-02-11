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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/** Platform agnostic API for operations required when retrieving data using the ProfilingSystem. */
public interface RecordingData {

  /**
   * @return the data stream.
   * @throws IOException if another IO-related problem occured.
   */
  InputStream getStream() throws IOException;

  /**
   * Releases the resources associated with the recording, for example the underlying file.
   *
   * <p>Forgetting to releasing this when done streaming, will need to one or more of the following:
   *
   * <ul>
   *   <li>Memory leak
   *   <li>File leak
   * </ul>
   *
   * <p>Please don't forget to call release when done streaming...
   */
  void release();

  /**
   * Returns the name of the recording from which the data is originating.
   *
   * @return the name of the recording from which the data is originating.
   */
  String getName();

  /**
   * Returns the requested start time for the recording.
   *
   * <p>Note that this doesn't necessarily have to match the time for the actual data recorded.
   *
   * @return the requested start time.
   */
  Instant getStart();

  /**
   * Returns the requested end time for the recording.
   *
   * <p>Note that this doesn't necessarily have to match the time for the actual data recorded.
   *
   * @return the requested end time.
   */
  Instant getEnd();
}
