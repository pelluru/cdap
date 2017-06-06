/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.etl.common.preview;

import co.cask.cdap.etl.api.Destroyable;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.Transformation;

/**
 * A {@link Transformation} that delegates transform operations while restricting the number of input emitted.
 *
 * @param <IN> Type of input object
 * @param <OUT> Type of output object
 */
public class LimitingTransform<IN, OUT> implements Transformation<IN, OUT>, Destroyable {

  private final Transformation<IN, OUT> transform;
  private final int numberOfRecordsLimited;
  private int numOfRecordsEmitted;

  public LimitingTransform(Transformation<IN, OUT> transform, int numberOfRecordsLimited) {
    this.transform = transform;
    this.numberOfRecordsLimited = numberOfRecordsLimited;
    this.numOfRecordsEmitted = 0;
  }

  @Override
  public void transform(IN input, Emitter<OUT> emitter) throws Exception {
    if (numOfRecordsEmitted++ < numberOfRecordsLimited) {
      transform.transform(input, emitter);
    }
  }

  @Override
  public void destroy() {
    if (transform instanceof Destroyable) {
      ((Destroyable) transform).destroy();
    }
  }
}
