/*
 * Copyright © 2014 Cask Data, Inc.
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
package co.cask.cdap.data2.transaction.queue;

/**
 * The state of a queue entry with respect to a consumer.
 */
public enum ConsumerEntryState {

  CLAIMED(1),
  PROCESSED(2);

  private final byte state;

  ConsumerEntryState(int state) {
    this.state = (byte) state;
  }

  public byte getState() {
    return state;
  }

  public static ConsumerEntryState fromState(byte state) {
    switch (state) {
      case 1:
        return CLAIMED;
      case 2:
        return PROCESSED;
      default:
        throw new IllegalArgumentException("Unknown state number " + state);
    }
  }
}
