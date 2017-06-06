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

package co.cask.cdap.api.data.stream;

import co.cask.cdap.api.annotation.Beta;
import co.cask.cdap.api.stream.StreamEventData;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Enabling Programs to write to Streams through their Context. This interface is marked as Beta since the API
 * might evolve in future releases until it matures.
 */
@Beta
public interface StreamWriter {

  /**
   * Writes a string to a stream
   *
   * @param stream stream id
   * @param data data to be written
   *
   * @throws IOException if an error occurred during write
   */
  void write(String stream, String data) throws IOException;

  /**
   * Writes a string to a stream with headers
   *
   * @param stream stream id
   * @param data data to be written
   * @param headers headers for the data
   *
   * @throws IOException if an error occurred during write
   */
  void write(String stream, String data, Map<String, String> headers) throws IOException;

  /**
   * Writes a {@link ByteBuffer} to a stream
   *
   * @param stream stream id
   * @param data {@link ByteBuffer} data to be written
   *
   * @throws IOException if an error occurred during write
   */
  void write(String stream, ByteBuffer data) throws IOException;

  /**
   * Writes a {@link StreamEventData} to a stream
   *
   * @param stream stream id
   * @param data {@link StreamEventData} data to be written
   *
   * @throws IOException if an error occurred during write
   */
  void write(String stream, StreamEventData data) throws IOException;

  /**
   * Writes a File to a stream in batch
   * @param stream stream id
   * @param file File
   * @param contentType content type
   *
   * @throws IOException if an error occurred during write
   */
  void writeFile(String stream, File file, String contentType) throws IOException;

  /**
   * Writes in batch using {@link StreamBatchWriter} to a stream
   * @param stream stream id
   * @param contentType content type
   * @return {@link StreamBatchWriter} provides a batch writer
   *
   * @throws IOException if an error occurred during write
   */
  StreamBatchWriter createBatchWriter(String stream, String contentType) throws IOException;
}
