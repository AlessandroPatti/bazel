// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.remote;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.build.lib.actions.ActionGraph;
import com.google.devtools.build.lib.actions.ActionInput;
import com.google.devtools.build.lib.actions.ExecutorInitException;
import com.google.devtools.build.lib.actions.SpawnStrategy;
import com.google.devtools.build.lib.analysis.ArtifactsToOwnerLabels;
import com.google.devtools.build.lib.exec.ExecutionOptions;
import com.google.devtools.build.lib.exec.ExecutorBuilder;
import com.google.devtools.build.lib.exec.ExecutorLifecycleListener;
import com.google.devtools.build.lib.exec.SpawnCache;
import com.google.devtools.build.lib.remote.options.RemoteOptions;
import com.google.devtools.build.lib.remote.util.DigestUtil;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.vfs.Path;
import javax.annotation.Nullable;

/** Provide a remote execution context. */
final class RemoteActionContextProvider implements ExecutorLifecycleListener {

  private final CommandEnvironment env;
  private final RemoteCache cache;
  @Nullable private final GrpcRemoteExecutor executor;
  @Nullable private final ListeningScheduledExecutorService retryScheduler;
  private final DigestUtil digestUtil;
  @Nullable private final Path logDir;
  private ImmutableSet<ActionInput> filesToDownload = ImmutableSet.of();

  private RemoteActionContextProvider(
      CommandEnvironment env,
      RemoteCache cache,
      @Nullable GrpcRemoteExecutor executor,
      @Nullable ListeningScheduledExecutorService retryScheduler,
      DigestUtil digestUtil,
      @Nullable Path logDir) {
    this.env = Preconditions.checkNotNull(env, "env");
    this.cache = Preconditions.checkNotNull(cache, "cache");
    this.executor = executor;
    this.retryScheduler = retryScheduler;
    this.digestUtil = digestUtil;
    this.logDir = logDir;
  }

  public static RemoteActionContextProvider createForRemoteCaching(
      CommandEnvironment env,
      RemoteCache cache,
      ListeningScheduledExecutorService retryScheduler,
      DigestUtil digestUtil) {
    return new RemoteActionContextProvider(
        env, cache, /*executor=*/ null, retryScheduler, digestUtil, /*logDir=*/ null);
  }

  public static RemoteActionContextProvider createForRemoteExecution(
      CommandEnvironment env,
      RemoteExecutionCache cache,
      GrpcRemoteExecutor executor,
      ListeningScheduledExecutorService retryScheduler,
      DigestUtil digestUtil,
      Path logDir) {
    return new RemoteActionContextProvider(
        env, cache, executor, retryScheduler, digestUtil, logDir);
  }

  /** Registers the action contexts whose lifecycle this class manages. */
  public void registerActionContexts(ExecutorBuilder executorBuilder) {
    ExecutionOptions executionOptions =
        checkNotNull(env.getOptions().getOptions(ExecutionOptions.class));
    RemoteOptions remoteOptions = checkNotNull(env.getOptions().getOptions(RemoteOptions.class));
    String buildRequestId = env.getBuildRequestId();
    String commandId = env.getCommandId().toString();

    if (executor == null) {
      RemoteSpawnCache spawnCache =
          new RemoteSpawnCache(
              env.getExecRoot(),
              remoteOptions,
              cache,
              buildRequestId,
              commandId,
              env.getReporter(),
              digestUtil,
              filesToDownload);
      executorBuilder.addActionContext(SpawnCache.class, spawnCache, "remote-cache");
    } else {
      RemoteSpawnRunner spawnRunner =
          new RemoteSpawnRunner(
              env.getExecRoot(),
              remoteOptions,
              env.getOptions().getOptions(ExecutionOptions.class),
              executionOptions.verboseFailures,
              env.getReporter(),
              buildRequestId,
              commandId,
              (RemoteExecutionCache) cache,
              executor,
              retryScheduler,
              digestUtil,
              logDir,
              filesToDownload,
              env.getBlazeWorkspace().getBinTools());
      executorBuilder.addActionContext(
          SpawnStrategy.class, new RemoteSpawnStrategy(env.getExecRoot(), spawnRunner), "remote");
    }
  }

  /** Returns the remote cache. */
  RemoteCache getRemoteCache() {
    return cache;
  }

  void setFilesToDownload(ImmutableSet<ActionInput> topLevelOutputs) {
    this.filesToDownload = Preconditions.checkNotNull(topLevelOutputs, "filesToDownload");
  }

  @Override
  public void executorCreated() throws ExecutorInitException {}

  @Override
  public void executionPhaseStarting(
      ActionGraph actionGraph, Supplier<ArtifactsToOwnerLabels> topLevelArtifactsToOwnerLabels)
      throws ExecutorInitException, InterruptedException {}

  @Override
  public void executionPhaseEnding() {
    cache.close();
    if (executor != null) {
      executor.close();
    }
  }
}
