/*
 * Copyright © 2014-2016 Cask Data, Inc.
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

/**
 * Annotation to declare that a method uses a {@link co.cask.cdap.api.dataset.Dataset}.
 *
 * <p>
 * Example:
 *
 * <pre><code>
 *   public class PurchaseStore extends AbstractFlowlet {
 *     {@literal @}UseDataSet("myTable")
 *     private ObjectStore{@literal <}Purchase> store;
 *     ...
 *     {@literal @}ProcessInput
 *     public void process(Purchase purchase) {
 *       store.write(Bytes.toBytes(purchase.getPurchaseTime()), purchase);
 *     }
 *     ...
 *   }
 * </code></pre>
 * 
 * <p>
 * See the 
 * <i><a href="http://docs.cask.co/cdap/current/en/developers-manual/index.html">CDAP Developers' Manual</a></i>
 * and the <a href="http://docs.cask.co/cdap/current/en/examples-manual/examples/purchase.html">example code for the
 * Purchase application</a> for more information.
 * </p>
 *
 * @see co.cask.cdap.api.flow.flowlet.Flowlet Flowlet
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UseDataSet {
  /**
   * Returns name of the {@link co.cask.cdap.api.dataset.Dataset}.
   */
  String value();
}
