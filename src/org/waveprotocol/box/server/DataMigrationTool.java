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

package org.waveprotocol.box.server;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;

import org.waveprotocol.box.server.persistence.migration.DataUtil;
import org.waveprotocol.box.server.persistence.migration.DeltaMigrator;

/**
 * A cmd line utility to perform data migration from a store type to another one.
 * Initially developed to replicate deltas from a file store to a mongodb store.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DataMigrationTool {

  private static final String USAGE_ERROR_MESSAGE =
      "\nUsage: DataMigrationTool <source options> <target options>\n" +
      "file options example: " + CoreSettings.DELTA_STORE_TYPE + "=" +
      CoreSettings.STORE_TYPE_FILE + "," + CoreSettings.DELTA_STORE_DIRECTORY + "=./_deltas\n" +
      "MongoDB options example: " + CoreSettings.DELTA_STORE_TYPE + "=" +
      CoreSettings.STORE_TYPE_MONGODB + "," + CoreSettings.MONGODB_HOST + "=127.0.0.1," +
      CoreSettings.MONGODB_PORT + "=27017," + CoreSettings.MONGODB_DATABASE + "=wiab";      
  
  public static void main(String[] args) {
    if (args.length != 2) {
      DataUtil.printAndExit(USAGE_ERROR_MESSAGE);
    }
    
    runDeltaMigration(args[0], args[1]);
  }
  
  private static void runDeltaMigration(String sourceOptions, String targetOptions) {
    try {
      Injector sourceInjector = DataUtil.createInjector(sourceOptions);
      Injector targetInjector = DataUtil.createInjector(targetOptions);
      
      // We can migrate data from-to any store type,
      // but it is not allowed to migrate between the same type
      String sourceDeltaStoreType = sourceInjector.getInstance(
          Key.get(String.class, Names.named(CoreSettings.DELTA_STORE_TYPE)));
      String targetDeltaStoreType = targetInjector.getInstance(
          Key.get(String.class, Names.named(CoreSettings.DELTA_STORE_TYPE)));
      if (sourceDeltaStoreType.equalsIgnoreCase(targetDeltaStoreType)) {
        DataUtil.printAndExit("Source and target delta store types must be different");
      }

      DeltaMigrator dm = new DeltaMigrator(
          sourceInjector.getInstance(DeltaStore.class),
          targetInjector.getInstance(DeltaStore.class));
      dm.run();      
    } catch (Exception e) {
      DataUtil.printAndExit(e.getMessage() + "\n" + USAGE_ERROR_MESSAGE);
    }
  }
}
