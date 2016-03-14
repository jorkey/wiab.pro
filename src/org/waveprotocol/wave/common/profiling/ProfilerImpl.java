/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.wave.common.profiling;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Accumulates statistic about running tasks.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ProfilerImpl implements Profiler {

  private boolean started = false;

  private Map<TaskName, TaskStatistic> statistic = new HashMap();
  private Set<TaskInfo> activeCalls = new HashSet<TaskInfo>();

  public ProfilerImpl() {
  }

  @Override
  public synchronized void start() {
    statistic.clear();
    activeCalls.clear();
    started = true;
  }

  @Override
  public synchronized void stop() {
    started = false;
  }

  @Override
  public synchronized ProfileTask taskStarted(TaskName taskName) {
    if (started) {
      ProfileTask task = new ProfileTask(this, taskName);
      activeCalls.add(task.getInfo());
      return task;
    }
    return null;
  }

  public synchronized void taskEnded(TaskInfo taskInfo) {
    if (started) {
      if (activeCalls.remove(taskInfo)) {
        TaskStatistic stat = statistic.get(taskInfo.getTaskName());
        if (stat == null) {
          stat = new TaskStatistic();
          statistic.put(taskInfo.getTaskName(), stat);
        }
        stat.executionsCount++;
        stat.executionTime += (new Date().getTime() - taskInfo.getStartTime());
      }
    }
  }

  @Override
  public synchronized Map<TaskName, TaskStatistic> getStatistic() {
    Map<TaskName, TaskStatistic> stat = new HashMap<TaskName, TaskStatistic>();
    for (Entry<TaskName, TaskStatistic> entry : statistic.entrySet()) {
      TaskStatistic task = new TaskStatistic();
      task.executionsCount = entry.getValue().getExecutionsCount();
      task.executionTime = entry.getValue().getExecutionTime();
      stat.put(entry.getKey(), task);
    }
    return stat;
  }

  @Override
  public synchronized Set<TaskInfo> getActiveCalls() {
    Set<TaskInfo> calls = new HashSet<TaskInfo>();
    for (TaskInfo call : activeCalls) {
      calls.add(new TaskInfo(call.getTaskName(), call.getStartTime()));
    }
    return calls;
  }
}
