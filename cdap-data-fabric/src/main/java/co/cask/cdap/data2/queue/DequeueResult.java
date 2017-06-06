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
package co.cask.cdap.data2.queue;


import com.google.common.collect.Iterators;

import java.util.Iterator;


/**
 * Represents result of an dequeue. The iterable gives dequeued data entries in the order of dequeue.
 *
 * @param <T> type of dequeue result
 */
public interface DequeueResult<T> extends Iterable<T> {

  /**
   * Returns {@code true} if there is no data in the queue.
   */
  boolean isEmpty();

  /**
   * Reclaim all dequeue entries represented by this result. The effect is to put entries represented by this
   * result back to the dequeued set of the queue consumer. Note that call to this method is transactional
   * and requires a new transaction on the {@link QueueConsumer} instance who provides the instance of this
   * {@link DequeueResult}.
   *
   * E.g.
   * <pre>
   *   startTransaction();
   *   DequeueResult result;
   *   try {
   *     result = consumer.dequeue();
   *     commitTransaction();
   *   } catch (Exception e) {
   *     rollbackTransaction();
   *
   *     // Skip the result.
   *     startTransaction();
   *     result.reclaim();
   *     commitTransaction();
   *   }
   *
   * </pre>
   */
  void reclaim();

  /**
   * Returns number of entries in this result.
   */
  int size();

  /**
   * Static helper class for creating empty result of different result type.
   */
  final class Empty {
    public static <T> DequeueResult<T> result() {
      return new DequeueResult<T>() {
        @Override
        public boolean isEmpty() {
          return true;
        }

        @Override
        public void reclaim() {
          // No-op
        }

        @Override
        public int size() {
          return 0;
        }

        @Override
        public Iterator<T> iterator() {
          return Iterators.emptyIterator();
        }
      };
    }
  }
}
