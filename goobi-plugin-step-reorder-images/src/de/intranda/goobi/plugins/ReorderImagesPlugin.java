package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

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
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j;
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

	private String sortingAlgorithm = "stanford";
	private String sourceFolderName;
	String targetFolderName;
	private boolean usePrefix = true;
	private boolean firstFileIsRight = false;
	private String namingFormat = "%04d";
	private List<String> blacklist = new ArrayList<String>();
	
	@Override
	public PluginReturnValue run() {
		if (sortingAlgorithm.equals("stanford")) {
			return sortingStanford();
		} else {
			return sortingStanford();
		}
	}

	public static void main(String[] args) throws IOException {
//		NIOFileUtils n = new NIOFileUtils();
//		n.deleteInDir(Paths.get("/opt/digiverso/goobi/metadata/6/images/schudiss_618299084_master"));
		
		
		
//		ReorderImagesPlugin rip = new ReorderImagesPlugin();
//	    String folder = "/Users/steffen/Downloads/edinburgh_reorder_start_small";
//	    rip.blacklist = new ArrayList<String>();
//	    rip.blacklist.add("_Colourchart");
//	    rip.blacklist.add("_Spine_1");
//	    
//	    Path pf = Paths.get(folder);
//	    List<Path> fileNames = new ArrayList<>();
//	    try (DirectoryStream<Path> stream = Files.newDirectoryStream(pf, rip.imageNameFilter)) {
//            for (Path entry : stream) {
//                fileNames.add(entry);
//            }
//        }
//        Collections.sort(fileNames);
//        for (Path path : fileNames) {
//            System.out.println(path.toString());
//        }
//
//        System.out.println("-------------------------");
//        List<Path> fileNamesCopy = new ArrayList<>(fileNames);
//        // sort out the blacklist files
//        for (String black : rip.blacklist) {
//            System.out.println(black);
//            for (Path f : fileNamesCopy) {
//                if (f.getFileName().toString().contains(black)) {
//                    System.out.println(f);
//                    fileNames.remove(f);
//                }
//            }
//        }
//        System.out.println("-------------------------");
//        for (Path path : fileNames) {
//            System.out.println(path.toString());
//        }
	}
	
	private DirectoryStream.Filter<Path> imageNameFilter = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path path) {
            String name = path.getFileName().toString();
            boolean fileOk = false;
            
            String prefix = "[\\w|\\W]*";
            if (name.matches(prefix + "\\.[Tt][Ii][Ff][Ff]?")) {
                fileOk = true;
            } else if (name.matches(prefix + "\\.[jJ][pP][eE]?[gG]")) {
                fileOk = true;
            } else if (name.matches(prefix + "\\.[jJ][pP][2]")) {
                fileOk = true;
            } else if (name.matches(prefix + "\\.[pP][nN][gG]")) {
                fileOk = true;
            } else if (name.matches(prefix + "\\.[gG][iI][fF]")) {
                fileOk = true;
            }
            return fileOk;
        }
    };
	
	
	/**
	 * Sorting algorithm for Stanfords Regis project
	 * 
	 * @return
	 */
	public PluginReturnValue sortingStanford() {
		
		try {
		    // 0. if target folder is different from source folder, move everything there first
		    if (!sourceFolderName.equals(targetFolderName)) {
		    	// cleanup target folder first, but this one is not working at all
		    	//StorageProvider.getInstance().deleteInDir(Paths.get(targetFolderName));
		    	// use the oldschool way then
		    	Path rootPath = Paths.get(targetFolderName);
				Files.walk(rootPath)
			      .sorted(Comparator.reverseOrder())
			      .map(Path::toFile)
			      .forEach(File::delete);
				StorageProvider.getInstance().createDirectories(rootPath);
				StorageProvider.getInstance().copyDirectory(Paths.get(sourceFolderName), Paths.get(targetFolderName));
		        sourceFolderName = targetFolderName;
		    }
		    
			// 1. load images from source folder
			List<Path> sourceFiles = StorageProvider.getInstance().listFiles(sourceFolderName,
					NIOFileUtils.imageNameFilter);
			// if no source files found, finish
			if (sourceFiles.isEmpty()) {
				Helper.addMessageToProcessLog(step.getProzess().getId(), LogType.ERROR,
						"Reordering of images could not be executed as the source folder is empty.");
				return PluginReturnValue.ERROR;
			}
			
			// remove blacklisted files from source files
			List<Path> blacklistedFiles = new ArrayList<Path>();
			if (blacklist.size()>0) {
			    List<Path> fileNamesCopy = new ArrayList<>(sourceFiles);
		        // sort out the blacklist files
		        for (String black : blacklist) {
		            for (Path f : fileNamesCopy) {
		                if (f.getFileName().toString().contains(black)) {
		                    sourceFiles.remove(f);
		                    blacklistedFiles.add(f);
		                }
		            }
		        }
			}

			// 3. find first and second half of images (even: images/2, odd images/2 + 1)
			List<Path> leftSideImages;
			List<Path> rightSideImages;
			if (sourceFiles.size() % 2 == 0) {
				// even
				if (firstFileIsRight) {
					leftSideImages = sourceFiles.subList(0, sourceFiles.size() / 2);
					rightSideImages = sourceFiles.subList(sourceFiles.size() / 2, sourceFiles.size());
					// reverse all right images to bring these to correct order
					Collections.reverse(rightSideImages);
				} else {
					rightSideImages = sourceFiles.subList(0, sourceFiles.size() / 2);
					leftSideImages = sourceFiles.subList(sourceFiles.size() / 2, sourceFiles.size());
					// reverse all left images to bring these to correct order
					Collections.reverse(leftSideImages);
				}
			} else {
				// odd
				Helper.addMessageToProcessLog(step.getProzess().getId(), LogType.ERROR,
						"Reordering of files stopped as there is an odd number of files.");
				return PluginReturnValue.ERROR;
			}

			// 4. rename left images to 1,3,5, ...
			int imageNumber = 1;
			runThroughFiles(leftSideImages, imageNumber, 2);

			// 5. rename right images to 2,4,6, ...
			imageNumber = 2;
			runThroughFiles(rightSideImages, imageNumber, 2);

			// 6. add blacklisted files to the end of all files
			if (blacklistedFiles.size()>0) {
			    List <Path> renamedBlacklistedFiles = new ArrayList<Path>();
			    for (Path bf : blacklistedFiles) {
			        String name = bf.getFileName().toString();
			        for (String s : blacklist) {
                        name = name.replace(s, "");
                    }
			        Path p = Paths.get(targetFolderName, name);
			        Files.move(bf, p);
			        renamedBlacklistedFiles.add(p);
                }
			    imageNumber = sourceFiles.size() + 1;
			    runThroughFiles(renamedBlacklistedFiles, imageNumber, 1);
			}
			
			// 7. remove temporary prefix 'goobi_' from all files
			sourceFiles = StorageProvider.getInstance().listFiles(targetFolderName, NIOFileUtils.imageNameFilter);
			for (Path image : sourceFiles) {
				String newImageFileName = image.getFileName().toString().substring(6,
						image.getFileName().toString().length());
				Path destination = Paths.get(targetFolderName, newImageFileName);
				Files.move(image, destination);
			}

		} catch (IOException e) {
			log.error("Error while reordering master images", e);
			Helper.addMessageToProcessLog(step.getProzess().getId(), LogType.ERROR,
					"Reordering of images could not be executed: " + e.getMessage());
			return PluginReturnValue.ERROR;
		}

		return PluginReturnValue.FINISH;
	}

	/**
	 * private method to run through all the files of a defined list (right or left pages only) and name these as expected

	 * @param pathes a list of all the files (right or left ones)
	 * @param imageNumber the start number from where to count for file naming
	 * @throws IOException
	 */
	private void runThroughFiles(List<Path> pathes, int imageNumber, int offset) throws IOException {
		for (Path image : pathes) {

			// just use a prefix if wanted (String with underscore followed)
			String prefix = "";
			if (usePrefix) {
				prefix = image.getFileName().toString();
				if (prefix.contains("_")) {
					prefix = image.getFileName().toString().substring(0, prefix.lastIndexOf("_") + 1);
				}
			}
			String newImageFileName = "goobi_" + prefix + String.format(namingFormat, imageNumber)
					+ getFileExtension(image.getFileName().toString());
			Path destination = Paths.get(targetFolderName, newImageFileName);
			if (targetFolderName.equals(sourceFolderName)) {
				Files.move(image, destination);
			} else {
				StorageProvider.getInstance().copyFile(image, destination);
			}
			imageNumber = imageNumber + offset;
		}
	}

	/**
	 * private method to get the file extension as string from a given file name
	 * 
	 * @param fileName name of a file
	 * @return the extesion of a file without leading dot
	 */
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
        this.returnPath = returnPath;
        this.step = step;
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        sortingAlgorithm = myconfig.getString("algorithm", "stanford");
		usePrefix = myconfig.getBoolean("usePrefix", true);
		firstFileIsRight = myconfig.getBoolean("firstFileIsRight", false);
		namingFormat = myconfig.getString("namingFormat", "%04d");
		blacklist = Arrays.asList(myconfig.getStringArray("blacklist"));
		try {
			// get source folder
			sourceFolderName = step.getProzess().getConfiguredImageFolder(myconfig.getString("sourceFolder", "master"));
			// get target folder
			targetFolderName = step.getProzess().getConfiguredImageFolder(myconfig.getString("targetFolder", "master"));
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
