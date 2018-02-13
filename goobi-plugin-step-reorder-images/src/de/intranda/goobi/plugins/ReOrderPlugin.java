package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j;

import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
@EqualsAndHashCode(callSuper = false)
public @Data class ReOrderPlugin implements IStepPluginVersion2 {

    private PluginGuiType pluginGuiType = PluginGuiType.NONE;
    private PluginType type = PluginType.Step;

    private String title = "plugin_intranda_step_reorder-images";

    private String pagePath = "";
    private Step step;
    private String returnPath;
    private Process process;

    @Override
    public PluginReturnValue run() {
        // 1. load images from master folder
        try {
            String masterFolderName = process.getImagesOrigDirectory(false);
//            String mediaFolderName = process.getImagesTifDirectory(false);

            List<Path> imagesInMasterFolder = NIOFileUtils.listFiles(masterFolderName, NIOFileUtils.imageNameFilter);

            if (imagesInMasterFolder.isEmpty()) {
                // no master images found, finish
                return PluginReturnValue.FINISH;
            }

//            // 2. check if media folder is empty
//            if (!NIOFileUtils.list(mediaFolderName).isEmpty()) {
//                // found images in media folder, cancel
//                // TODO get error text from messages
//                String message = "destination folder is not empty.";
//                Helper.setFehlerMeldung(message);
//                Helper.addMessageToProcessLog(process.getId(), LogType.ERROR, message);
//                return PluginReturnValue.ERROR;
//            }
//            
//            Path mediaFolder = Paths.get(mediaFolderName);
//            if (!Files.exists(mediaFolder)) {
//                Files.createDirectories(mediaFolder);
//            }

            // 3. find first and second half of images (even: images/2, odd images/2 + 1)
            List<Path> leftSideImages;
            List<Path> rightSideImages;
            if (imagesInMasterFolder.size() % 2 == 0) {
                // even
                leftSideImages = imagesInMasterFolder.subList(0, imagesInMasterFolder.size() / 2);
                rightSideImages = imagesInMasterFolder.subList(imagesInMasterFolder.size() / 2 , imagesInMasterFolder.size());
            } else {
                // odd
                leftSideImages = imagesInMasterFolder.subList(0, imagesInMasterFolder.size() / 2 + 1);
                rightSideImages = imagesInMasterFolder.subList(imagesInMasterFolder.size() / 2 + 1, imagesInMasterFolder.size());
            }
            // 4. rename first half to 1,3,5, ...
            int imageNumber = 1;
            for (Path image : leftSideImages) {
                String prefix = image.getFileName().toString();
                if (prefix.contains("_")) {
                    prefix = image.getFileName().toString().substring(0,  prefix.lastIndexOf("_") +1);
                }
                String newImageFileName = "goobi_" + prefix+String.format("%04d", imageNumber) + getFileExtension(image.getFileName().toString());
                Path destination = Paths.get(masterFolderName, newImageFileName);
                Files.move(image, destination);
//                NIOFileUtils.copyFile(image, destination);
                imageNumber = imageNumber + 2;
            }

            // 5. rename second half to 2,4,6, ...

            imageNumber = 2;
            for (Path image : rightSideImages) {
                String prefix = image.getFileName().toString();
                if (prefix.contains("_")) {
                    prefix = image.getFileName().toString().substring(0,  prefix.lastIndexOf("_") +1);
                }
                String newImageFileName = "goobi_" + prefix+ String.format("%04d", imageNumber) + getFileExtension(image.getFileName().toString());
                Path destination = Paths.get(masterFolderName, newImageFileName);
                Files.move(image, destination);
//                NIOFileUtils.copyFile(image, destination);
                imageNumber = imageNumber + 2;
            }

            imagesInMasterFolder = NIOFileUtils.listFiles(masterFolderName, NIOFileUtils.imageNameFilter);
            for (Path image : imagesInMasterFolder) {
            		String newImageFileName = image.getFileName().toString().substring(6, image.getFileName().toString().length());
                Path destination = Paths.get(masterFolderName, newImageFileName);
                Files.move(image, destination);
            }
            
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
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
