/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.stat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Converter of statistics table content to text suitable for using in spreadsheets.
 * Adds data missing in the table columns.
 * 
 * Source file: input.txt
 * Source file format: plain text copied from statistics window
 * Destination file: output.csv
 * Destination file format: CSV with TAB separation symbol
 * 
 * @author dyukon@gmail.com
 */
public class StatToSpreadsheetConverterUtil {
  
  public static void main(String[] args) throws FileNotFoundException, IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader("input.txt")) ) {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.csv")) ) {
        String line = reader.readLine();
        while (line != null) {
          line = line.trim();
          if (line.length() > 0) {
            line = processLine(line);
            writer.write(line);
            writer.newLine();
          }  
          line = reader.readLine();
        }
        writer.close();
      }
      reader.close();
    }  
  }
  
  private static String processLine(String line) {
    line = removeDuplicates(line, "\t");
    String[] values = line.split("\t");
    if (values.length == 6) {
      return line;
    }
    if (values.length == 2) {
      return values[0]        // Name
          + "\t1"             // Count
          + "\t" + values[1]  // Average
          + "\t" + values[1]  // Lowest
          + "\t" + values[1]  // Highest
          + "\t" + values[1]; // Total
    }
    throw new RuntimeException("Invalid values count (" + values.length + ") in line: " + line);
  }
  
  private static String removeDuplicates(String line, String fragment) {
    while (line.contains(fragment + fragment)) {
      line = line.replace(fragment + fragment, fragment);
    }
    return line;
  }
}
