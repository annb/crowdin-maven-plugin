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
package org.exoplatform.crowdin.mojo;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.exoplatform.crowdin.model.CrowdinFile.Type;
import org.exoplatform.crowdin.model.CrowdinFileFactory;
import org.exoplatform.crowdin.model.CrowdinTranslation;
import org.exoplatform.crowdin.model.SourcesRepository;
import org.exoplatform.crowdin.utils.PropsToXML;

/**
 * @author Philippe Aristote
 */
@Mojo(name = "update")
public class UpdateSourcesMojo extends AbstractCrowdinMojo {

  @Override
  public void executeMojo() throws MojoExecutionException, MojoFailureException {
    getLog().info("Preparing projects ...");
    for (SourcesRepository repository : getSourcesRepositories()) {
      try {
        // Create or update the reference repository
        File bareRepository = new File(getStartDir(), repository.getName() + ".git");
        if (bareRepository.exists()) {
          Git git = Git.open(bareRepository);
          getLog().info("Fetching repository " + repository.getName() + " ...");
          git.fetch().call();
          getLog().info("Done.");
        } else {
          getLog().info("Cloning repository " + repository.getName() + " ...");
          Git git = Git.cloneRepository().setURI(repository.getUri()).
              setDirectory(bareRepository).setRemote("origin").
              setBare(true).setNoCheckout(true).call();
          getLog().info("Done.");
        }
        // Create a copy for the given version
        File localVersionRepository = new File(getStartDir(), repository.getLocalDirectory());
        if (localVersionRepository.exists()) {
          Git git = Git.open(localVersionRepository);
          getLog().info("Reset repository " + repository.getLocalDirectory() + "...");
          git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/" + repository.getBranch()).call();
          git.clean().setCleanDirectories(true).call();
          getLog().info("Done.");
        } else {
          getLog().info("Creating working repository " + localVersionRepository + " ...");
          Git git = Git.cloneRepository().setURI(bareRepository.getAbsolutePath()).setDirectory(localVersionRepository).setNoCheckout(true).call();
          StoredConfig config = git.getRepository().getConfig();
          config.setString("remote", "origin", "url", repository.getUri());
          config.save();
          git.checkout().setStartPoint("origin/" + repository.getBranch()).setName(repository.getBranch()).setCreateBranch(true).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call();
          getLog().info("Done.");
        }
      } catch (Exception e) {
        throw new MojoExecutionException("Error while initializing project " + repository.getName(), e);
      }
    }
    getLog().info("Projects ready.");
    File zip = new File(getProject().getBuild().getDirectory(), "all.zip");
    if (!zip.exists()) {
      try {
        getHelper().setApprovedOnlyOption();
        getLog().info("Downloading Crowdin translation zip...");
        getHelper().downloadTranslations(zip);
        getLog().info("Downloading done!");
      } catch (Exception e) {
        getLog().error("Error downloading the translations from Crowdin. Exception:\n" + e.getMessage());
      }
    }
    extractZip(getStartDir(), zip.getPath());
    //get the translations status
    File status_trans = new File(getProject().getBasedir(), "report/translation_status.xml");
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(status_trans));
      writer.write(getHelper().getTranslationStatus());
    } catch (IOException e) {
    } finally {
      try {
        if (writer != null)
          writer.close();
      } catch (IOException e) {
      }
    }
    for (SourcesRepository repository : getSourcesRepositories()) {
      try {
        File localVersionRepository = new File(getStartDir(), repository.getLocalDirectory());
        Git git = Git.open(localVersionRepository);
        StoredConfig config = git.getRepository().getConfig();
        config.setStringList(ConfigConstants.CONFIG_CORE_SECTION, null, "whitespace", Arrays.asList("trailing-space", "space-before-tab", "indent-with-non-tab", "cr-at-eol"));
        config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
                       ConfigConstants.CONFIG_KEY_AUTOCRLF, CoreConfig.AutoCRLF.INPUT);
        config.save();
        // Create a patch with local changes
        getLog().info("Create patch for " + repository.getLocalDirectory() + "...");
        File patchFile = new File(getProject().getBuild().getDirectory(), repository.getLocalDirectory() + ".patch");
        if (patchFile.exists()) patchFile.delete();
        OutputStream fos = new BufferedOutputStream(new FileOutputStream(patchFile));
        DiffFormatter diffFormatter = new DiffFormatter(fos);
        try {
          diffFormatter.setRepository(git.getRepository());
          diffFormatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
          diffFormatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
          List<DiffEntry> originalEntries = diffFormatter.scan(new DirCacheIterator(git.getRepository().readDirCache()), new FileTreeIterator(git.getRepository()));
          List<DiffEntry> cleanedEntries = new ArrayList<DiffEntry>();
          for (DiffEntry originalEntry : originalEntries) {
            // We manually remove all empty hunks
            if (!diffFormatter.toFileHeader(originalEntry).toEditList().isEmpty()) {
              cleanedEntries.add(originalEntry);
            }
          }
          diffFormatter.format(cleanedEntries);
        } finally {
          diffFormatter.flush();
          diffFormatter.release();
          fos.close();
        }
        getLog().info("Done.");
        // Reset our local copy
        getLog().info("Reset repository " + repository.getLocalDirectory() + "...");
        git.reset().setMode(ResetCommand.ResetType.HARD).call();
        git.clean().setCleanDirectories(true).call();
        getLog().info("Done.");
        // Apply the patch
        getLog().info("Apply patch for " + repository.getLocalDirectory() + "...");
        InputStream fis = new BufferedInputStream(new FileInputStream(patchFile));
        try {
          ApplyResult result = git.apply().setPatch(fis).call();
          for (File updatedFile : result.getUpdatedFiles()) {
            getLog().info("File updated : " + updatedFile.getPath());
          }
        } catch (PatchApplyException pee) {
          getLog().error("Error while applying patch " + patchFile + " on " + repository.getLocalDirectory() + " : " + pee.getMessage());
          continue;
        } finally {
          fis.close();
        }
        getLog().info("Done.");
        getLog().info("Commit changes for " + repository.getLocalDirectory() + "...");
        // Commit changes
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Apply changes for " + getLangs() + " on " + repository.getName() + " " + repository.getVersion()).call();
        getLog().info("Done.");
        getLog().info("Pushing changes for " + repository.getLocalDirectory() + "...");
        // Push it
        //git.push().setDryRun(isDryRun()).call();
        getLog().info("Done.");
      } catch (Exception e) {
        throw new MojoExecutionException("Error while updating project " + repository.getName(), e);
      }
    }

  }


  private void extractZip(File _destFolder, String _zipFile) {
    try {
      byte[] buf = new byte[1024];
      List<String> langs = Arrays.asList(getLangs().split(","));
      ZipInputStream zipinputstream = null;
      ZipEntry zipentry;
      zipinputstream = new ZipInputStream(new FileInputStream(_zipFile));

      zipentry = zipinputstream.getNextEntry();
      while (zipentry != null) {
        // for each entry to be extracted
        if (zipentry.isDirectory()) {
          zipentry = zipinputstream.getNextEntry();
          continue;
        }
        String zipentryName = zipentry.getName();
        getLog().debug("Processing " + zipentryName);
        zipentryName = CrowdinFileFactory.encodeMinusCharacterInPath(zipentryName, false);
        zipentryName = zipentryName.replace('/', File.separatorChar);
        zipentryName = zipentryName.replace('\\', File.separatorChar);
        String[] path = zipentryName.split(File.separator);
        String lang = path[0];
        String crowdinProj = path[1];
        String proj = path[2];
        String fileName = "";

        // process only the languages specified
        if (!(langs.contains("all") || langs.contains(lang))) {
          zipentry = zipinputstream.getNextEntry();
          continue;
        }

        try {
          String cp = crowdinProj + File.separator + proj;
          Properties currentProj = getProperties().get(proj + "/");
          // ignore projects that is not managed by the plugin
          if (currentProj == null) {
            zipentry = zipinputstream.getNextEntry();
            continue;
          }
          String key = zipentryName.substring(zipentryName.indexOf(cp) + cp.length() + 1);
          String value = currentProj.getProperty(key);
          if (value == null) {
            zipentry = zipinputstream.getNextEntry();
            continue;
          }
          zipentryName = zipentryName.substring(0, zipentryName.indexOf(proj) + proj.length());

          lang = CrowdinTranslation.encodeLanguageName(lang, false);

          fileName = value.substring(value.lastIndexOf(File.separatorChar) + 1);

          getLog().info("Updating " + zipentryName + " - " + value.substring(0, value.lastIndexOf(File.separatorChar) + 1) + fileName);

          String name = fileName.substring(0, fileName.lastIndexOf("."));
          String extension = fileName.substring(fileName.lastIndexOf("."));
          if (name.lastIndexOf("_en") > 0) {
            name = name.substring(0, name.lastIndexOf("_en"));
          }

          if (key.contains("gadget") || value.contains("gadget")) {
            if ("default".equalsIgnoreCase(name)) {
              fileName = lang + extension;
            } else if (name.contains("_ALL")) {
              fileName = lang + "_ALL" + extension;
            } else {
              fileName = name + "_" + lang + extension;
            }

          } else {
            fileName = name + "_" + lang + extension;
          }

          String parentDir = _destFolder + File.separator + proj + File.separator + value.substring(0, value.lastIndexOf(File.separatorChar) + 1);
          parentDir = parentDir.replace('/', File.separatorChar).replace('\\', File.separatorChar);
          String entryName = parentDir + fileName;
          Type resourceBundleType = (key.indexOf("gadget") >= 0) ? Type.GADGET : Type.PORTLET;

          File newFile = new File(entryName.substring(0, entryName.lastIndexOf(File.separatorChar)));
          newFile.mkdirs();

          // Need improve, some portlets in CS use xml format for vi, ar locales
          boolean isXML = (entryName.indexOf(".xml") > 0);

          if (isXML) {
            // create the temporary properties file to be used for PropsToXML (use the file in Crowdin zip)
            entryName = entryName.replaceAll(".xml", ".properties");
            int n;
            FileOutputStream fileoutputstream;
            fileoutputstream = new FileOutputStream(entryName);
            while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
              fileoutputstream.write(buf, 0, n);
            }
            fileoutputstream.close();

            File propertiesFile = new File(entryName);
            PropsToXML.execShellCommand("native2ascii -encoding UTF8 " + propertiesFile.getPath() + " " + propertiesFile.getPath());
            PropsToXML.parse(propertiesFile.getPath(), resourceBundleType);
            propertiesFile.delete();
          } else {
            // identify the master properties file
            String masterFile = parentDir + name + extension;
            if (!new File(masterFile).exists()) masterFile = parentDir + name + "_en" + extension;
            if (!new File(masterFile).exists()) throw new FileNotFoundException("Cannot create or update " + entryName + " as the master file " + name + extension + " (or " + name + "_en" + extension + ")" + " does not exist!");

            // use the master file as a skeleton and fill in with translations from Crowdin
            PropertiesConfiguration config = new PropertiesConfiguration(masterFile);
            PropertiesConfiguration.setDefaultListDelimiter('=');
            config.setEncoding("UTF-8");

            Properties props = new Properties();
            props.load(zipinputstream);
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
              String propKey = (String) e.nextElement();
              config.setProperty(propKey, props.getProperty(propKey));
            }

            // if language is English, update master file and the English file if it exists (do not create new)
            if ("en".equals(lang)) {
              config.save(masterFile);
              // perform post-processing for the output file
              //use shell script
              //ShellScriptUtils.execShellscript("scripts/per-file-processing.sh", masterFile);
              //use java
              org.exoplatform.crowdin.utils.FileUtils.replaceCharactersInFile(masterFile, "config/special_character_processing.properties", "UpdateSourceSpecialCharacters");

              if (new File(entryName).exists()) {
                config.save(entryName);
                //use shell script
                //ShellScriptUtils.execShellscript("scripts/per-file-processing.sh", entryName);
                //use java
                org.exoplatform.crowdin.utils.FileUtils.replaceCharactersInFile(entryName, "config/special_character_processing.properties", "UpdateSourceSpecialCharacters");

              }
            } else {
              // always create new (or update) for other languages
              config.save(entryName);
              //use shell script
              //ShellScriptUtils.execShellscript("scripts/per-file-processing.sh", entryName);
              //user java
              org.exoplatform.crowdin.utils.FileUtils.replaceCharactersInFile(entryName, "config/special_character_processing.properties", "UpdateSourceSpecialCharacters");

            }
          }

          zipinputstream.closeEntry();
        } catch (Exception e) {
          getLog().warn("Error while applying change for " + zipentryName + " - " + fileName + " : " + e.getMessage());
        }
        zipentry = zipinputstream.getNextEntry();
      }// while

      zipinputstream.close();
    } catch (Exception e) {
      getLog().error("Update aborted !", e);
    }
  }
}
