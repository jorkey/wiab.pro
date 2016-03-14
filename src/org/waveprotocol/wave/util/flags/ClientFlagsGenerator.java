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
package org.waveprotocol.wave.util.flags;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Generator for FlagConstants.java and ClientFlagsBase.java.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ClientFlagsGenerator {
  
  private static final String FLAG_CONSTANTS_CLASS_NAME = "FlagConstants";
  private static final String CLIENT_FLAGS_CLASS_NAME = "ClientFlags";
    
  private static final int INDENT_SPACE_COUNT = 2;
  
  private static final Map<Integer, String> indentsBySize = new HashMap<>();
  
  private final String defaultConfigFileName;
  private final String generatedPackageName;
  private final String generatedFilesDir;
  
  private StringBuilder builder;
  private int indentSize;
  
  public static void main(String[] args) {
    ClientFlagsGenerator generator = new ClientFlagsGenerator(args[0], args[1], args[2]);
    generator.work();
  }
  
  public ClientFlagsGenerator(String defaultConfigFileName, String generatedPackageName,
      String generatedFilesDir) {
    this.defaultConfigFileName = defaultConfigFileName;
    this.generatedPackageName = generatedPackageName;
    this.generatedFilesDir = generatedFilesDir;
  }
  
  public void work() {
    List<Parameter> parameters = extractParametersFromDefaultConfigFile(defaultConfigFileName);
    
    generateFlagConstants(parameters);
    saveToFile(generatedFilesDir + FLAG_CONSTANTS_CLASS_NAME + ".java");

    generateClientFlags(parameters);
    saveToFile(generatedFilesDir + CLIENT_FLAGS_CLASS_NAME + ".java");    
  }
  
  private static List<Parameter> extractParametersFromDefaultConfigFile(String fileName) {
    try {
      List<Parameter> parameters = new ArrayList<>();
      PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
      Iterator<String> keys = configuration.getKeys();
      while (keys.hasNext()) {
        String key = keys.next();
        parameters.add(Parameter.create(key, configuration.getString(key).trim(),
            configuration.getLayout().getComment(key)) );
      }
      return parameters;
    } catch (ConfigurationException e) {
      throw new RuntimeException(e);
    }
  }  
  
  private void generateFlagConstants(List<Parameter> parameters) {
    builder = new StringBuilder();
    indentSize = 0;
    
    appendDoNotEditWarning();
    appendLine();
    appendPackageName();
    appendLine();
    appendLine("import java.util.HashMap;");
    appendLine("import java.util.Map;");    
    appendLine();
    appendClassComment("Contains tags shared between client and server for passing runtime flags.");
    appendClassHeader(FLAG_CONSTANTS_CLASS_NAME);
    appendLine();

    indentSize++;    
    int count = 0;
    for (Parameter parameter : parameters) {
      appendLinF("public static final String %s = \"%d\";", parameter.getKey().toUpperCase(), count++);
    }
    
    appendLine();    
    appendLine("private static final Map<String, String> NAME_MAPPING = new HashMap<>();");    
    appendLine("static {");    
    indentSize++;
    count = 0;
    for (Parameter parameter : parameters) {
      appendLinF("NAME_MAPPING.put(\"%s\", \"%d\");", parameter.getSmallCamelCaseKey(), count++);
    }
    indentSize--;
    appendLine("};");
    
    appendLine();
    appendLine("public static String getShortName(String name) {");
    appendLine("  return NAME_MAPPING.get(name);");
    appendLine("}");
    
    indentSize--;
    appendLine("}");
  }
  
  private void generateClientFlags(List<Parameter> parameters) {
    builder = new StringBuilder();
    indentSize = 0;
    
    appendDoNotEditWarning();    
    appendLine();
    appendPackageName();
    appendLine();
    appendClassComment("Contains definitions, getters and initialization code for flags.");
    appendClassHeader(CLIENT_FLAGS_CLASS_NAME);
    
    indentSize++;
    
    for (Parameter parameter : parameters) {
      Enumeration enumeration = parameter.getEnumeration();
      if (enumeration != null) {
        appendLine();
        appendLine("/**");
        appendLinF(" * Enumeration for \"%s\" parameter.", parameter.getSmallCamelCaseKey());
        appendLine(" */");
        appendLinF("public enum %s {", enumeration.getName());
        
        indentSize++;
        List<String> values = enumeration.getValues();
        int remainCount = values.size();
        for (String value : values) {
          appendLinF("%s%s", value.toUpperCase(), --remainCount > 0 ? "," : "");
        }
        indentSize--;
        
        appendLine("}");
      }  
    }
    
    for (Parameter parameter : parameters) {
      appendLine();
      appendLinF("private final %s %s;", parameter.getType(), parameter.getSmallCamelCaseKey());
    }
    
    appendLine();
    appendLine("/**");
    appendLine(" * Debug information for flag values.");
    appendLine(" *");
    appendLine(" * @return array of pairs (name, value) for each flag.");
    appendLine(" *");
    appendLine(" * WARNING(danilatos): !! This method leaks our flag names. Ensure it is");
    appendLine(" * never called in permutations that can be exposed to the public, so that");
    appendLine(" * it gets compiled out in those cases.");
    appendLine(" */");
    appendLine("public Object[] getDebugPairs() {");
    
    indentSize++;    
    appendLine("return new Object[] {");
    
    indentSize += 2;
    for (Parameter parameter : parameters) {
      appendLinF("\"%1$s\", %1$s,", parameter.getSmallCamelCaseKey());
    }
    
    indentSize -= 2;    
    appendLine("};");
    indentSize--;
    appendLine("}");
    
    appendLine();    
    appendLine("/**");
    appendLine(" * Constructor which populates fields using ClientFlagsBaseHelper.");
    appendLine(" *");
    appendLine(" * @param helper helper to get parameter values.");
    appendLine(" */");
    appendLinF("public %s(ClientFlagsHelper helper) {", CLIENT_FLAGS_CLASS_NAME);
    
    indentSize++;
    for (Parameter parameter : parameters) {
      Enumeration enumeration = parameter.getEnumeration();
      if (enumeration != null) {
        appendLinF("%s = %s.valueOf(helper.getString(%s.%s, \"%s\").toUpperCase());",
            parameter.getSmallCamelCaseKey(),
            enumeration.getName(),
            FLAG_CONSTANTS_CLASS_NAME,
            parameter.getKey().toUpperCase(),
            parameter.getValue());
      } else {
        appendLinF("%s = helper.get%s(%s.%s, %s);",
            parameter.getSmallCamelCaseKey(),
            parameter.getCapitalCaseType(),
            FLAG_CONSTANTS_CLASS_NAME,
            parameter.getKey().toUpperCase(),
            parameter.getValue());
      }  
    }
    indentSize--;
    
    appendLine("}");
    
    for (Parameter parameter : parameters) {
      appendLine();
      appendGetterComment(parameter.getSmallCamelCaseKey(), parameter.getComment());
      appendLinF("public %s %s() {", parameter.getType(), parameter.getSmallCamelCaseKey());
      appendLinF("  return %s;", parameter.getSmallCamelCaseKey());
      appendLine("}");
    }    
    indentSize--;
    
    appendLine("}");
  }
  
  private void appendLine() {
    builder.append("\n");
  }  
  
  private void appendLine(String line) {
    builder.append(getIndentBySize(indentSize)).append(line).append("\n");
  }
  
  private void appendLinF(String format, Object... args) {
    appendLine(String.format(format, args));
  }
  
  private void appendPackageName() {
    appendLinF("package %s;", generatedPackageName);
  }

  private void appendDoNotEditWarning() {
    appendLine("// ===================================================================");
    appendLine("//");
    appendLine("//   WARNING: GENERATED CODE! DO NOT EDIT!");
    appendLine("//");
    appendLine("// ===================================================================");
    appendLine("//");
    appendLine("// This file is generated");
    appendLine("//");
    appendLine("//   by /src/org/waveprotocol/wave/util/flags/ClientFlagsGenerator.java");
    appendLine("// from /client.default.config");
  }
  
  private void appendClassComment(String comment) {
    appendLine("/**");
    appendLinF(" * %s", comment);
    appendLine(" */");
  }
  
  private void appendGetterComment(String field, String comment) {
    appendLine("/**");
    if (comment != null) {
      for (String commentLine : comment.split("\n")) {
        appendLinF(" * %s", commentLine.substring(1).trim());
      }  
      appendLine(" *");
    }  
    appendLinF(" * @return %s", field);
    appendLine(" */");    
  }
  
  private void appendClassHeader(String className) {
    appendLine("public class " + className + " {");
  }
  
  private String getIndentBySize(int indentSize) {
    String indent = indentsBySize.get(indentSize);
    if (indent == null) {
      indent = StringUtils.repeat(" ", indentSize * INDENT_SPACE_COUNT);
      indentsBySize.put(indentSize, indent);
    }
    return indent;
  }
  
  private void saveToFile(String fileName) {
    String text = builder.toString();
    try (PrintWriter printWriter = new PrintWriter(fileName)) {
      printWriter.println(text);
      printWriter.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    System.out.println(fileName + " has been created.");    
  }
}
