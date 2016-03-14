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

package org.waveprotocol.box.server.persistence.migration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;

import org.waveprotocol.wave.federation.Proto;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.util.settings.Setting;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;

/**
 * Library for data migration and preparation tools containing common features.
 * 
 * @author pablojan@gmail.com (Pablo Ojanguren)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DataUtil {

  static class GeneratorModule extends AbstractModule {
    @Provides
    @Singleton
    public IdGenerator provideIdGenerator(IdGeneratorImpl.Seed seed) {
      return new IdGeneratorImpl("dummy", seed);
    }
    
    @Provides
    @Singleton
    public IdGeneratorImpl.Seed provideSeed(final SecureRandom random) {
      return new IdGeneratorImpl.Seed() {
        @Override
        public String get() {
          return Long.toString(Math.abs(random.nextLong()), 36);
        }
      };
    }

    @Override
    protected void configure() {
    }
  }
  
  // Pubic methods
    
  /**
   * Prints message and exits application.
   * 
   * @param message message to print
   */
  public static void printAndExit(String message) {
    System.out.println(message);
    System.exit(1);
  }
  
  public static Injector createInjector(String cmdLineSettings) {
    Module settings = bindCmdLineSettings(cmdLineSettings);
    Injector settingsInjector = Guice.createInjector(settings);
    Module persistenceModule = settingsInjector.getInstance(PersistenceModule.class);
    Module executorsModule = settingsInjector.getInstance(ExecutorsModule.class);
    Module generatorModule = settingsInjector.getInstance(GeneratorModule.class);
    
    return settingsInjector.createChildInjector(persistenceModule, executorsModule, generatorModule);
  }
  
  public static WaveletDelta deserialize(
      ByteStringMessage<Proto.ProtocolAppliedWaveletDelta> appliedDelta)
      throws InvalidProtocolBufferException {
    Proto.ProtocolAppliedWaveletDelta pawDelta = appliedDelta.getMessage();
    Proto.ProtocolSignedDelta psDelta = pawDelta.getSignedOriginalDelta();
    Proto.ProtocolWaveletDelta pwDelta =
        ByteStringMessage.parseProtocolWaveletDelta(psDelta.getDelta()).getMessage();
    return OperationSerializer.deserialize(pwDelta);    
  }
  
  // Private methods
  
  private static Module bindCmdLineSettings(String cmdLineProperties) {
    // Get settings from cmd line, e.g.
    // Key = delta_store_type
    // Value = mongodb
    final Map<String, String> propertyMap = new HashMap<>();
    for (String arg : cmdLineProperties.split(",")) {
      String[] argTokens = arg.split("=");
      propertyMap.put(argTokens[0], argTokens[1]);
    }
    // Validate settings against CoreSettings
    final Map<Setting, Field> coreSettings = getCoreSettings();
    // Set a suitable map to match cmd line settings
    Map<String, Setting> propertyToSettingMap = new HashMap<>();
    for (Setting s : coreSettings.keySet()) {
      propertyToSettingMap.put(s.name(), s);
    }
    for (String propertyKey : propertyMap.keySet()) {
      if (!propertyToSettingMap.containsKey(propertyKey)) {
        throw new RuntimeException("Wrong setting '" + propertyKey + "'\n");
      }  
    }
    return new AbstractModule() {

      @Override
      protected void configure() {
        // We must iterate the settings when binding.
        // Note: do not collapse these loops as that will damage
        // early error detection. The runtime is still O(n) in setting count.
        for (Map.Entry<Setting, Field> entry : coreSettings.entrySet()) {
          Setting setting = entry.getKey();
          Class<?> type = entry.getValue().getType();
          String value = propertyMap.containsKey(setting.name()) ? propertyMap.get(setting.name())
              : setting.defaultValue();
          if (int.class.equals(type)) {
            // Integer defaultValue = null;
            // if (!setting.defaultValue().isEmpty()) {
            // defaultValue = Integer.parseInt(setting.defaultValue());
            // }
            bindConstant().annotatedWith(Names.named(setting.name())).to(Integer.parseInt(value));
          } else if (boolean.class.equals(type)) {
            // Boolean defaultValue = null;
            // if (!setting.defaultValue().isEmpty()) {
            // defaultValue = Boolean.parseBoolean(setting.defaultValue());
            // }
            bindConstant().annotatedWith(Names.named(setting.name())).to(Boolean.parseBoolean(value));
          } else if (String.class.equals(type)) {
            bindConstant().annotatedWith(Names.named(setting.name())).to(value);
          } else {
            /** Not supported **/
            /*
             * String[] value = config.getStringArray(setting.name()); if
             * (value.length == 0 && !setting.defaultValue().isEmpty()) { value
             * = setting.defaultValue().split(","); } bind(new
             * TypeLiteral<List<String>>()
             * {}).annotatedWith(Names.named(setting.name()))
             * .toInstance(ImmutableList.copyOf(value));
             */
          }
        }
      }
    };
  }  
  
  private static Map<Setting, Field> getCoreSettings() {
    // Get all method fields
    Field[] coreSettingFields = CoreSettings.class.getDeclaredFields();
    // Filter only annotated fields
    Map<Setting, Field> settings = new HashMap<>();
    for (Field f : coreSettingFields) {
      if (f.isAnnotationPresent(Setting.class)) {
        Setting setting = f.getAnnotation(Setting.class);
        settings.put(setting, f);
      }
    }
    return settings;
  }  
}
