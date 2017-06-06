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

package co.cask.cdap.internal.app.runtime.distributed;

import co.cask.cdap.app.guice.DefaultProgramRunnerFactory;
import co.cask.cdap.app.runtime.ProgramRunner;
import co.cask.cdap.app.runtime.ProgramRunnerFactory;
import co.cask.cdap.app.runtime.ProgramRuntimeProvider;
import co.cask.cdap.internal.app.runtime.batch.MapReduceProgramRunner;
import co.cask.cdap.internal.app.runtime.workflow.WorkflowProgramRunner;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ProgramId;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Modules;
import org.apache.twill.api.TwillContext;

import javax.annotation.Nullable;

/**
 *
 */
final class WorkflowTwillRunnable extends AbstractProgramTwillRunnable<WorkflowProgramRunner> {

  WorkflowTwillRunnable(String name) {
    super(name);
  }

  @Override
  protected Module createModule(TwillContext context, ProgramId programId, String runId, String instanceId,
                                @Nullable String principal) {
    Module module = super.createModule(context, programId, runId, instanceId, principal);
    return Modules.combine(module, new PrivateModule() {
      @Override
      protected void configure() {
        // Bind ProgramRunner for MR, which is used by Workflow.
        // The ProgramRunner for Spark is provided by the DefaultProgramRunnerFactory through the extension mechanism
        MapBinder<ProgramType, ProgramRunner> runnerFactoryBinder =
          MapBinder.newMapBinder(binder(), ProgramType.class, ProgramRunner.class);
        runnerFactoryBinder.addBinding(ProgramType.MAPREDUCE).to(MapReduceProgramRunner.class);

        // It uses local mode factory because for Workflow we launch the job from the Workflow container directly.
        // The actual execution mode of the job is governed by the framework configuration
        // For mapreduce, it's in the mapred-site.xml
        // for spark, it's in the hConf we shipped from DistributedWorkflowProgramRunner
        bind(ProgramRuntimeProvider.Mode.class).toInstance(ProgramRuntimeProvider.Mode.LOCAL);
        bind(ProgramRunnerFactory.class).to(DefaultProgramRunnerFactory.class).in(Scopes.SINGLETON);
        expose(ProgramRunnerFactory.class);
      }
    });
  }

  @Override
  protected boolean propagateServiceError() {
    // Don't propagate Workflow failure as failure. Quick fix for CDAP-749.
    return false;
  }
}
