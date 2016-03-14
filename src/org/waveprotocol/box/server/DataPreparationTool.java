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
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;

import org.waveprotocol.box.server.persistence.migration.DataUtil;
import org.waveprotocol.box.server.persistence.migration.DeltaPreparator;

/**
 * A cmd line utility to perform data preparation.
 * Based on DataMigrationTool.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DataPreparationTool {
  
  private static final String DEFAULT_OPTIONS =
      CoreSettings.DELTA_STORE_TYPE + "=" + CoreSettings.STORE_TYPE_FILE + "," + 
      CoreSettings.DELTA_STORE_DIRECTORY + "=" + "./_deltas";
  
  private static final String USAGE_ERROR_MESSAGE =
      "\nUsage: DataPreparationTool <wave id> [<options>]\n" +
      "file options example (used by default): " + CoreSettings.DELTA_STORE_TYPE + "=" +
      CoreSettings.STORE_TYPE_FILE + "," + CoreSettings.DELTA_STORE_DIRECTORY + "=./_deltas\n" +
      "mongodb options example: " + CoreSettings.DELTA_STORE_TYPE + "=" +
      CoreSettings.STORE_TYPE_MONGODB + "," + CoreSettings.MONGODB_HOST + "=127.0.0.1," +
      CoreSettings.MONGODB_PORT + "=27017," + CoreSettings.MONGODB_DATABASE + "=wiab\n";      
  
  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      DataUtil.printAndExit(USAGE_ERROR_MESSAGE);
    }
    
    String waveId = args[0];
    String options = args.length == 1 ? DEFAULT_OPTIONS : args[1];    
    runDeltaPreparation(waveId, options);
  }
  
  private static void runDeltaPreparation(String waveId, String options) {
    try {
      Injector injector = DataUtil.createInjector(options);
      DeltaPreparator dp = new DeltaPreparator(injector.getInstance(DeltaStore.class), waveId);
      dp.run();          
    } catch (Exception e) {
      DataUtil.printAndExit(e.getMessage() + "\n" + USAGE_ERROR_MESSAGE);
    }    
  }  
}
