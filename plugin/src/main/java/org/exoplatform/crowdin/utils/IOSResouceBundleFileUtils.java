/*
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.crowdin.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

/**
 * This class contains the utilities to inject Crowdin translation file to iOS resource file 
 */
public class IOSResouceBundleFileUtils {

  static Log log;
  static Boolean isStillComment = false;

  static List<String> crowdinList ;
  static List<String> resourcelist ;

  public static void setLog(Log varLog) {
    log = varLog;
  }

  public static Log getLog() {
    if (log == null) {
      log = new SystemStreamLog();
    }

    return log;
  }

  /*
   * Read all lines of iOS resource file to list
   * One line will input to one list's item
   */
  public static List<String> readAllIOSResource(String filePath) {
    List<String> output = new ArrayList<String>();
    try {
      File file = new File(filePath);
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = "";

      while ((line = reader.readLine()) != null) {
        output.add(line);
      }
      reader.close();
      return output;
    } catch (IOException ioe) {
      log.error(ioe);
      return new ArrayList<String>();
    }
  }

  /*
   * Read all lines of iOS resource file to list
   * One line will input to one list's item
   * Don't add comment lines and empty lines
   */
  public static List<String> readIOSResourceSkipCommentAndEmtyLine(String filePath) {
    List<String> output = new ArrayList<String>();
    try {
      File file = new File(filePath);
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = "";
      boolean isCommentOrEmptyLine = false;
      while ((line = reader.readLine()) != null) {
        if (line.trim().indexOf("//") == 0 || line.trim().length() == 0) {
          continue;
        }
        
        if(line.trim().indexOf("/*") == 0){
          isCommentOrEmptyLine=true;
          continue;
        }
        
        if(line.trim().indexOf("*/") >= 0){
          isCommentOrEmptyLine=false;
          continue;
        }
        
        if(isCommentOrEmptyLine==false){
          output.add(line);
        }
          
      }
      reader.close();
      return output;
    } catch (IOException ioe) {
      log.error(ioe);
      return new ArrayList<String>();
    }
  }

/**
 * Update translation from crowdin a crowdin file line to a resouce bundle file line
 * verify if key translation is changed, if yes apply to source
 * @param sourceLine 
 * @param crowdinLine
 * @return
 */
  public static boolean updateTranslationByLine(int index,String sourceLine, String crowdinLine) {
    if(sourceLine.trim().length()==0 || crowdinLine.trim().length() ==0)
      return false;
    try{
      String sourceKey = sourceLine.split("=")[0].trim();
      String crowdinKey = crowdinLine.split("=")[0].trim();
      if(sourceKey.equals(crowdinKey)){
        StringBuffer buffer = new  StringBuffer(sourceKey);
        String crowdinValue = crowdinLine.split("=")[1].trim();
        buffer.append(" = ").append(crowdinValue);
        sourceLine = buffer.toString();
        getLog().info("=======updateTranslationByLine ====" + sourceLine);
        resourcelist.set(index, sourceLine);
        return true;
      }      
      
      return false;
    }catch (Exception e) {
      getLog().error("=======updateTranslationByLine ====" + e.getMessage());
      
      return false;
    }
  }

  /*
   * Check a line is comment or empty line
   */
  public static boolean isCommentOrEmptyLine(int lineIndex, List<String> linesOfFile) {
    if (linesOfFile == null || linesOfFile.isEmpty())
      return false;

    String lineStr = linesOfFile.get(lineIndex).trim();

    if (lineStr.length() == 0){
      return true;
    }
    else if (lineStr.startsWith("/*")){
      isStillComment = true;
      return true;
    }
    else if (lineStr.endsWith("*/")){
      isStillComment = false;
      return true;
    }
    else if(isStillComment){
      return true;
    }
    else if (lineStr.indexOf("//") == 0 ){
      return true;
    }
    else if (!isStillComment){
      return false;
    }
    

//    int checkIndex = lineIndex;
//
//    while (checkIndex >= 0) {
//      String previousLineString = linesOfFile.get(checkIndex).trim();
//      if (previousLineString.indexOf("//") == 0 || previousLineString.indexOf("/*") == 0)
//        return true;
//      else
//        checkIndex--;
//    }
    return false;
  }

  /*
   * Inject translation from crowdin translation file to resouce bundle file
   */
  public static boolean injectTranslation(String crowdinFilePath, String resourceMasterFilePath, String resoureTranslationFilePath) {
    crowdinList = readIOSResourceSkipCommentAndEmtyLine(crowdinFilePath);
    resourcelist = readAllIOSResource(resourceMasterFilePath);

    getLog().info("=======crowdinFilePath===="+ crowdinFilePath);
    File crowdinFile = new File(crowdinFilePath);     
    crowdinFile.delete();
    
    if (resourcelist == null || resourcelist.isEmpty())
      return false;

    for (int resouceIndex = 0; resouceIndex <= resourcelist.size(); resouceIndex++) {
      getLog().info("=======injectTranslation1====");
      if (isCommentOrEmptyLine(resouceIndex, resourcelist) == false) {

        for (int crowdinIndex = 0; crowdinIndex <= crowdinList.size(); crowdinIndex++) {
          if (updateTranslationByLine(resouceIndex, resourcelist.get(resouceIndex), crowdinList.get(crowdinIndex))) {
            crowdinList.remove(crowdinIndex);
            break;
          }
        }
      }
      getLog().info("=======injectTranslation2====");
      
    }
    getLog().info("=======saveListStringToFile====");
    
    return saveListStringToFile(resoureTranslationFilePath, resourcelist);
  }

  /*
   * Save a list<String> that contains iOS resouce bundle file lines to file
   */
  public static boolean saveListStringToFile(String filePath, List<String> listString) {
    try {
      StringBuffer content = new StringBuffer();

      for (String str : listString) {
        content.append(str + System.getProperty("line.separator"));
      }
      FileWriter writer = new FileWriter(filePath);
      writer.write(content.toString());
      writer.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return false;
    }
    return true;
  }

}
