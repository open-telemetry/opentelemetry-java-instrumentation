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

/** Listener for getting notified when new recording data is becoming available. */
public interface RecordingDataListener {
  /**
   * Called when new recording data becomes available. Handle quickly, e.g. typically schedule
   * streaming of the new available data in another thread. Do not forget to {@link
   * RecordingData#release()} when the data has been uploaded.
   *
   * @param type type of the recording
   * @param data the new data available
   */
  void onNewData(RecordingType type, RecordingData data);
}
