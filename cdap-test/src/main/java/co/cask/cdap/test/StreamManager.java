/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.test;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.flow.flowlet.StreamEvent;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * This interface helps to interact with streams.
 */
@Beta
public interface StreamManager {

  /**
   * Create the stream.
   *
   * @throws java.io.IOException If there is an error creating the stream.
   */
  void createStream() throws IOException;

  /**
   * Sends a UTF-8 encoded string to the stream.
   * @param content Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(String content) throws IOException;

  /**
   * Sends a byte array to the stream. Same as calling {@link #send(byte[], int, int) send(content, 0, content.length)}.
   * @param content Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(byte[] content) throws IOException;

  /**
   * Sends a byte array to the stream.
   * @param content Data to be sent.
   * @param off Offset in the array to start with
   * @param len Number of bytes to sent starting from {@code off}.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(byte[] content, int off, int len) throws IOException;

  /**
   * Sends the content of a {@link java.nio.ByteBuffer} to the stream.
   * @param buffer Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(ByteBuffer buffer) throws IOException;

  /**
   * Sends a UTF-8 encoded string to the stream.
   * @param headers Key-value pairs to be sent as
   *                headers of {@link StreamEvent StreamEvent}.
   * @param content Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(Map<String, String> headers, String content) throws IOException;

  /**
   * Sends a byte array to the stream. Same as calling {@link #send(byte[], int, int) send(content, 0, content.length)}.
   * @param headers Key-value pairs to be sent as
   *                headers of {@link StreamEvent StreamEvent}.
   * @param content Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(Map<String, String> headers, byte[] content) throws IOException;

  /**
   * Sends a byte array to the stream.
   * @param headers Key-value pairs to be sent as
   *                headers of {@link StreamEvent StreamEvent}.
   * @param content Data to be sent.
   * @param off Offset in the array to start with
   * @param len Number of bytes to sent starting from {@code off}.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(Map<String, String> headers, byte[] content, int off, int len) throws IOException;

  /**
   * Sends the content of a {@link java.nio.ByteBuffer} to the stream.
   * @param headers Key-value pairs to be sent as
   *                headers of {@link StreamEvent StreamEvent}.
   * @param buffer Data to be sent.
   * @throws java.io.IOException If there is error writing to the stream.
   */
  void send(Map<String, String> headers, ByteBuffer buffer) throws IOException;

  /**
   * Sends the content of a file to the stream. Use this to load events into a stream in batch.
   *
   * @param file the file to send to the stream
   * @throws IOException if there is an error writing to the stream
   */
  void send(File file, String contentType) throws Exception;

  /**
   * Get events from the specified stream in the specified interval
   *
   * @param startTime the start time in milliseconds or "now-xs" format
   * @param endTime the end time in milliseconds or "now-xs" format
   * @param limit the maximum number of events to return
   * @return a list of stream events in the given time range
   */
   List<StreamEvent> getEvents(String startTime, String endTime, int limit) throws IOException;

  /**
   * Get events from the specified stream in the specified interval
   *
   * @param startTime the start time in milliseconds
   * @param endTime the end time in milliseconds
   * @param limit the maximum number of events to return
   * @return a list of stream events in the given time range
   */
  List<StreamEvent> getEvents(long startTime, long endTime, int limit) throws IOException;
}
