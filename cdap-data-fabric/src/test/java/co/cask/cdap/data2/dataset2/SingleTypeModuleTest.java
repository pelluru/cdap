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

package co.cask.cdap.data2.dataset2;

import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.DatasetSpecification;
import co.cask.cdap.api.dataset.module.EmbeddedDataset;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 *
 */
public class SingleTypeModuleTest {
  @Test
  public void testCtorCheck() {
    assertGood(Good1.class);
    assertGood(Good2.class);
    assertGood(Good3.class);
    assertBad(Bad1.class);
    assertBad(Bad2.class);
    assertBad(Bad3.class);
    assertBad(Bad4.class);
    assertBad(Bad5.class);
    assertBad(Bad6.class);
    assertBad(Bad7.class);
    assertBad(Bad8.class);
    assertBad(Bad9.class);
  }

  private void assertGood(Class<? extends Dataset> cl) {
    Assert.assertNotNull(SingleTypeModule.findSuitableCtorOrFail(cl));
  }

  private void assertBad(Class<? extends Dataset> cl) {
    try {
      SingleTypeModule.findSuitableCtorOrFail(cl);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public static final class Bad1 extends AbstractDataset {
    public Bad1() {
    }
  }
  
  public static final class Bad2 extends AbstractDataset {
    public Bad2(int i) {
    }
  }
  
  public static final class Bad3 extends AbstractDataset {
    public Bad3(Object o) {
    }
  }
    
  public static final class Good1 extends AbstractDataset {
    public Good1(DatasetSpecification spec) {
    }
  }

  public static final class Bad4 extends AbstractDataset {
    public Bad4(DatasetSpecification spec, int k) {
    }
  }

  public static final class Bad5 extends AbstractDataset {
    public Bad5(DatasetSpecification spec, Object foo) {
    }
  }

  public static final class Bad6 extends AbstractDataset {
    public Bad6(DatasetSpecification spec, Dataset ds) {
    }
  }

  public static final class Good2 extends AbstractDataset {
    public Good2(DatasetSpecification spec, @EmbeddedDataset("foo") Dataset ds) {
    }
  }

  public static final class Bad7 extends AbstractDataset {
    public Bad7(DatasetSpecification spec, @EmbeddedDataset("foo") Dataset ds, int i) {
    }
  }

  public static final class Good3 extends AbstractDataset {
    public Good3(DatasetSpecification spec, @EmbeddedDataset("foo") Dataset ds, @EmbeddedDataset("bar") Dataset bar) {
    }
  }

  public static final class Bad8 extends AbstractDataset {
    public Bad8(DatasetSpecification spec,
                @EmbeddedDataset("foo") Dataset ds,
                int i,
                @EmbeddedDataset("bar") Dataset bar) {
    }
  }


  public static final class Bad9 extends AbstractDataset {
    public Bad9(DatasetSpecification spec,
                DatasetSpecification spec2,
                @EmbeddedDataset("foo") Dataset ds) {
    }
  }

  public abstract static class AbstractDataset implements Dataset {
    @Override
    public void close() throws IOException {
    }
  }
}
