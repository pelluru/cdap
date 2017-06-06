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

package co.cask.cdap.common.collect;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This collector will collect every entry.
 *
 * @param <KEY> Type of key
 * @param <VALUE> Type of value
 */
public class AllPairCollector<KEY, VALUE> implements PairCollector<KEY, VALUE> {

  private final List<Map.Entry<KEY, VALUE>> elements = new ArrayList<>();

  @Override
  public boolean addElement(Map.Entry<KEY, VALUE> entry) {
    elements.add(Maps.immutableEntry(entry.getKey(), entry.getValue()));
    return true;
  }

  @Override
  public <T extends Multimap<? super KEY, ? super VALUE>> T finishMultimap(T map) {
    for (Map.Entry<KEY, VALUE> entry : elements) {
      map.put(entry.getKey(), entry.getValue());
    }
    elements.clear();
    return map;
  }

  @Override
  public <T extends Collection<? super Map.Entry<KEY, VALUE>>> T finish(T collection) {
    collection.addAll(elements);
    elements.clear();
    return collection;
  }
}
