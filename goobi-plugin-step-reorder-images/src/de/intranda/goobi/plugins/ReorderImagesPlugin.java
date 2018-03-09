package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.event.ListSelectionEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
@EqualsAndHashCode(callSuper = false)
public @Data class ReorderImagesPlugin implements IStepPluginVersion2 {

    private PluginGuiType pluginGuiType = PluginGuiType.NONE;
    private PluginType type = PluginType.Step;

    private String title = "intranda_step_reorder_images";

    private String pagePath = "";
    private Step step;
    private String returnPath;
    private Process process;
    
    private String sortingAlgorithm = "stanford";
    String sourceFolderName;
    String targetFolderName;
   
    @Override
    public PluginReturnValue run() {
    		if (sortingAlgorithm.equals("stanford")) {
    			return sortingStanford();
    		} else {
    			return sortingStanford();
    		}
    }
    
    	/**
    	 * Sorting algorithm for Stanfords Regis project
    	 * @return
    	 */
    	public PluginReturnValue sortingStanford() {
    		boolean firstFileIsRight = false;
    		try {
           
            // 1. load images from master folder
            List<Path> sourceFiles = NIOFileUtils.listFiles(sourceFolderName, NIOFileUtils.imageNameFilter);
            // if no master images found, finish
            if (sourceFiles.isEmpty()) {
            		Helper.addMessageToProcessLog(process.getId(), LogType.ERROR, "Reordering of images could not be executed as the master folder is empty.");
                return PluginReturnValue.ERROR;
            }

            // 3. find first and second half of images (even: images/2, odd images/2 + 1)
            List<Path> leftSideImages;
            List<Path> rightSideImages;
            if (sourceFiles.size() % 2 == 0) {
                // even
            		if (firstFileIsRight) {
            			leftSideImages = sourceFiles.subList(0, sourceFiles.size() / 2);
            			rightSideImages = sourceFiles.subList(sourceFiles.size() / 2 , sourceFiles.size());
            			// reverse all right images to bring these to correct order
            			Collections.reverse(rightSideImages);
            		} else {
                		rightSideImages = sourceFiles.subList(0, sourceFiles.size() / 2);
                    leftSideImages = sourceFiles.subList(sourceFiles.size() / 2 , sourceFiles.size());
                    // reverse all left images to bring these to correct order
                    Collections.reverse(leftSideImages);
            		}
            } else {
                // odd
            		Helper.addMessageToProcessLog(process.getId(), LogType.ERROR, "Reordering of files stopped as there is an odd number of files.");
                return PluginReturnValue.ERROR;
            }
            
            // 4. rename left images to 1,3,5, ...
            int imageNumber = 1;
            for (Path image : leftSideImages) {
                String prefix = image.getFileName().toString();
                if (prefix.contains("_")) {
                    prefix = image.getFileName().toString().substring(0,  prefix.lastIndexOf("_") +1);
                }
                String newImageFileName = "goobi_" + prefix+String.format("%04d", imageNumber) + getFileExtension(image.getFileName().toString());
                Path destination = Paths.get(targetFolderName, newImageFileName);
                if (targetFolderName.equals(sourceFolderName)) {
                		Files.move(image, destination);
                	}else {
                		NIOFileUtils.copyFile(image, destination);
                	}
                	imageNumber = imageNumber + 2;
            }

            // 5. rename right images to 2,4,6, ...
            imageNumber = 2;
            for (Path image : rightSideImages) {
                String prefix = image.getFileName().toString();
                if (prefix.contains("_")) {
                    prefix = image.getFileName().toString().substring(0,  prefix.lastIndexOf("_") +1);
                }
                String newImageFileName = "goobi_" + prefix+ String.format("%04d", imageNumber) + getFileExtension(image.getFileName().toString());
                Path destination = Paths.get(targetFolderName, newImageFileName);
                if (targetFolderName.equals(sourceFolderName)) {
            			Files.move(image, destination);
	            	}else {
	            		NIOFileUtils.copyFile(image, destination);
	            	}
                imageNumber = imageNumber + 2;
            }

            // 6. remove temporary prefix 'goobi_' from all files
            sourceFiles = NIOFileUtils.listFiles(targetFolderName, NIOFileUtils.imageNameFilter);
            for (Path image : sourceFiles) {
            		String newImageFileName = image.getFileName().toString().substring(6, image.getFileName().toString().length());
                Path destination = Paths.get(targetFolderName, newImageFileName);
                Files.move(image, destination);
            }
            
        } catch (IOException e) {
            log.error("Error while reordering master images", e);
            Helper.addMessageToProcessLog(process.getId(), LogType.ERROR, "Reordering of images could not be executed: " + e.getMessage() );
            return PluginReturnValue.ERROR;
        }

        return PluginReturnValue.FINISH;
    }
    


    private String getFileExtension(String fileName) {
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
            return fileName.substring(fileName.lastIndexOf("."));
        } else {
            return "";
        }
    }

    @Override
    public boolean execute() {
        PluginReturnValue check = run();
        if (check.equals(PluginReturnValue.FINISH)) {
            return true;
        }
        return false;
    }

    public String cancel() {
        return returnPath;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        process = step.getProzess();
        this.returnPath = returnPath;

        String projectName = step.getProzess().getProjekt().getTitel();
		XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
		xmlConfig.setExpressionEngine(new XPathExpressionEngine());
		xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

		SubnodeConfiguration myconfig = null;

		// order of configuration is:
		// 1.) project name and step name matches
		// 2.) step name matches and project is *
		// 3.) project name matches and step name is *
		// 4.) project name and step name are *
		try {
			myconfig = xmlConfig
					.configurationAt("//config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
		} catch (IllegalArgumentException e) {
			try {
				myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '" + step.getTitel() + "']");
			} catch (IllegalArgumentException e1) {
				try {
					myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '*']");
				} catch (IllegalArgumentException e2) {
					myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
				}
			}
		}

		sortingAlgorithm = myconfig.getString("algorithm", "stanford");
		try {
			// get source folder
			if (myconfig.getString("sourceFolder", "master").equals("master")) {
				sourceFolderName = step.getProzess().getImagesOrigDirectory(false);
			} else {
				sourceFolderName = step.getProzess().getImagesTifDirectory(false);
			}
			// get target folder
			if (myconfig.getString("targetFolder", "master").equals("master")) {
				targetFolderName = step.getProzess().getImagesOrigDirectory(false);
			} else {
				targetFolderName = step.getProzess().getImagesTifDirectory(false);
			}
			
			// create target folder it not exists
            Path targetFolder = Paths.get(targetFolderName);
            if (!Files.exists(targetFolder)) {
            		Files.createDirectories(targetFolder);
            }
            
		} catch (SwapException | DAOException | IOException | InterruptedException e) {
			log.error(e);
		}

    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {
        return 2;
    }

}
