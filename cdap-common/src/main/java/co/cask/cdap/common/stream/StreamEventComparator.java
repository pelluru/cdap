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
package co.cask.cdap.common.stream;

import co.cask.cdap.api.flow.flowlet.StreamEvent;
import com.google.common.primitives.Longs;

import java.util.Comparator;

/**
 * A {@link Comparator} for {@link StreamEvent} that compares with event timestamp in ascending order.
 */
public final class StreamEventComparator implements Comparator<StreamEvent> {

  @Override
  public int compare(StreamEvent o1, StreamEvent o2) {
    return Longs.compare(o1.getTimestamp(), o2.getTimestamp());
  }
}
