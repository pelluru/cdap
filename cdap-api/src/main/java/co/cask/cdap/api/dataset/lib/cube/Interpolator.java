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
package co.cask.cdap.api.dataset.lib.cube;

import co.cask.cdap.api.annotation.Beta;

/**
 * Defines how to interpolate a value between two other time values.
 * <p/>
 * It is used to fill in empty timestamps in the result of {@link CubeQuery} when needed.
 */
@Beta
public interface Interpolator {

  /**
   * Given start and end TimeValues, and a time in-between the two, return a TimeValue for the in-between time.
   */
  long interpolate(TimeValue start, TimeValue end, long ts);

  /**
   * Data points that are more than this many seconds apart will not cause interpolation to occur and will instead
   * return 0 for any point in between.
   */
  long getMaxAllowedGap();
}
