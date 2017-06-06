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
package co.cask.cdap.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotates a Flowlet’s method to indicate that, instead of processing data objects from a Flowlet input, this
 * method is invoked periodically without arguments.
 *
 * <p>
 * For example, this can be used to generate data or to pull data from an external data source periodically on a fixed
 * cadence.
 * </p>
 *
 * <pre>
 * <code>
 * public class RandomSource extends AbstractFlowlet {
 *   private OutputEmitter{@literal <}Integer> randomOutput;
 *
 *   private final Random random = new Random();
 *
 *   {@literal @}Tick(delay = 1L, unit = TimeUnit.MILLISECONDS)
 *   public void generate() throws InterruptedException {
 *     randomOutput.emit(random.nextInt(10000));
 *   }
 * }
 * </code>
 * </pre>
 *
 * @see co.cask.cdap.api.flow.flowlet.Flowlet
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tick {

  // Due to a bug in checkstyle, it would emit false positives here of the form
  // "Unused Javadoc tag (line:col)" for each of the default clauses.
  // This comment disables that check up to the corresponding ON comments below

  // CHECKSTYLE OFF: Unused Javadoc tag

  /**
   * Initial delay before calling the tick method for the first time. Default is {@code 0}.
   *
   * @return Time for the initial delay.
   */
  long initialDelay() default 0L;

  /**
   * Time to delay between the termination of one tick call and the start of the next one.
   *
   * @return Time to delay between calls.
   */
  long delay();

  /**
   * Time unit for both {@link #initialDelay()} and {@link #delay()}. Default is {@link TimeUnit#SECONDS}.
   *
   * @return The time unit.
   */
  TimeUnit unit() default TimeUnit.SECONDS;

  /**
   * Optionally specifies the maximum number of retries of failure inputs before giving up.
   * Defaults to 0 (no retry).
   */
  int maxRetries() default 0;

  // CHECKSTYLE ON
}
