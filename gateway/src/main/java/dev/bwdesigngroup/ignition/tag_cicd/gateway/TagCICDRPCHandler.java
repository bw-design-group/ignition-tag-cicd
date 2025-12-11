package dev.bwdesigngroup.ignition.tag_cicd.gateway;

import dev.bwdesigngroup.ignition.tag_cicd.common.model.ExportMode;
import dev.bwdesigngroup.ignition.tag_cicd.common.constants.TagCICDConstants;
import dev.bwdesigngroup.ignition.tag_cicd.common.TagCICDRPC;
import dev.bwdesigngroup.ignition.tag_cicd.common.util.FileUtilities;
import dev.bwdesigngroup.ignition.tag_cicd.common.util.TagExportUtilities;
import dev.bwdesigngroup.ignition.tag_cicd.common.util.TagImportUtilities;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TagCICDRPCHandler implements TagCICDRPC {
    private static final Logger logger = LoggerFactory.getLogger(TagCICDRPCHandler.class.getName());
    private final GatewayContext context;
    private final Gson gson = new Gson();

    public TagCICDRPCHandler(GatewayContext context) {
        this.context = context;
    }

    @Override
    public String exportTags(String provider, String baseTagPath, String filePath, boolean recursive,
            boolean localPropsOnly, String exportMode, boolean deleteExisting, boolean excludeUdtDefinitions) {
        JsonObject result = new JsonObject();
        try {
            logger.info("RPC exportTags called: provider={}, baseTagPath={}, filePath={}, exportMode={}",
                    provider, baseTagPath, filePath, exportMode);
            TagExportUtilities.exportTagsToDisk(context.getTagManager(), provider, baseTagPath, recursive,
                    localPropsOnly, filePath, exportMode, deleteExisting, excludeUdtDefinitions);
            result.addProperty("success", true);
            result.addProperty("filePath", filePath);
            result.addProperty("exportMode", exportMode);
            result.addProperty("details", "Exported tags to " + filePath + " using " +
                    ExportMode.fromCode(exportMode).getDisplayName() + " mode");
        } catch (Exception e) {
            logger.error("Error exporting tags: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Failed to export tags: " + e.getMessage());
        }
        return result.toString();
    }

    @Override
    public String importTags(String provider, String baseTagPath, String sourcePath, String collisionPolicy,
            String exportMode) {
        JsonObject result = new JsonObject();
        try {
            logger.info("RPC importTags called: provider={}, baseTagPath={}, sourcePath={}, exportMode={}",
                    provider, baseTagPath, sourcePath, exportMode);
            JsonObject importResult = TagImportUtilities.importTagsFromSource(context.getTagManager(), provider,
                    baseTagPath, sourcePath, collisionPolicy, exportMode);
            result.addProperty("success", true);
            result.addProperty("exportMode", exportMode);
            result.add("details", importResult);
        } catch (Exception e) {
            logger.error("Error importing tags: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Failed to import tags: " + e.getMessage());
        }
        return result.toString();
    }

    @Override
    public String getTagConfig() {
        JsonArray result = new JsonArray();
        Path configPath = Paths.get(TagCICDConstants.CONFIG_FILE_PATH);
        if (!Files.exists(configPath)) {
            logger.warn("No export-config.json found at {}", configPath.toAbsolutePath());
            return result.toString();
        }

        try {
            String configContent = new String(Files.readAllBytes(configPath));
            result = new JsonParser().parse(configContent).getAsJsonArray();
        } catch (Exception e) {
            logger.error("Error retrieving tag config: {}", e.getMessage(), e);
        }
        return result.toString();
    }

    @Override
    public String getExportModes() {
        JsonArray modesArray = new JsonArray();

        for (ExportMode mode : ExportMode.values()) {
            JsonObject modeObject = new JsonObject();
            modeObject.addProperty("code", mode.getCode());
            modeObject.addProperty("name", mode.getDisplayName());
            modesArray.add(modeObject);
        }

        return modesArray.toString();
    }

    @Override
    public String exportTagsFromConfig() {
        JsonObject result = new JsonObject();
        Path configPath = Paths.get(TagCICDConstants.CONFIG_FILE_PATH);
        if (!Files.exists(configPath)) {
            logger.error("No export-config.json found at {}", configPath.toAbsolutePath());
            result.addProperty("success", false);
            result.addProperty("error", "Config file not found at " + configPath.toAbsolutePath());
            return result.toString();
        }

        try {
            String configContent = new String(Files.readAllBytes(configPath));
            JsonArray configArray = new JsonParser().parse(configContent).getAsJsonArray();
            JsonObject exportResults = new JsonObject();

            for (JsonElement element : configArray) {
                JsonObject config = element.getAsJsonObject();
                String filePath = config.get("sourcePath").getAsString();
                String provider = config.get("provider").getAsString();
                String baseTagPath = config.get("baseTagPath").getAsString();
                String exportMode = config.get("exportMode").getAsString();
                boolean excludeUdtDefinitions = config.has("excludeUdtDefinitions")
                        ? config.get("excludeUdtDefinitions").getAsBoolean()
                        : false;

                logger.info("Exporting tags from config: filePath={}, provider={}, baseTagPath={}, exportMode={}",
                        filePath, provider, baseTagPath, exportMode);

                TagExportUtilities.exportTagsToDisk(context.getTagManager(), provider, baseTagPath, true, false,
                        filePath, exportMode, true, excludeUdtDefinitions);

                exportResults.addProperty(filePath, "Exported successfully using " +
                        ExportMode.fromCode(exportMode).getDisplayName() + " mode");
            }
            result.addProperty("success", true);
            result.add("details", exportResults);
        } catch (Exception e) {
            logger.error("Error exporting tags from config: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Failed to export tags from config: " + e.getMessage());
        }
        return result.toString();
    }

    @Override
    public String importTagsFromConfig() {
        JsonObject result = new JsonObject();
        Path configPath = Paths.get(TagCICDConstants.CONFIG_FILE_PATH);
        if (!Files.exists(configPath)) {
            logger.error("No export-config.json found at {}", configPath.toAbsolutePath());
            result.addProperty("success", false);
            result.addProperty("error", "Config file not found at " + configPath.toAbsolutePath());
            return result.toString();
        }

        try {
            String configContent = new String(Files.readAllBytes(configPath));
            JsonArray configArray = new JsonParser().parse(configContent).getAsJsonArray();
            JsonObject importResults = new JsonObject();

            for (JsonElement element : configArray) {
                JsonObject config = element.getAsJsonObject();
                String sourcePath = config.get("sourcePath").getAsString();
                String provider = config.get("provider").getAsString();
                String baseTagPath = config.get("baseTagPath").getAsString();
                String collisionPolicy = config.get("collisionPolicy").getAsString();
                String exportMode = config.get("exportMode").getAsString();

                logger.info("Importing tags from config: sourcePath={}, provider={}, baseTagPath={}, exportMode={}",
                        sourcePath, provider, baseTagPath, exportMode);

                JsonObject importResult = TagImportUtilities.importTagsFromSource(context.getTagManager(), provider,
                        baseTagPath, sourcePath, collisionPolicy, exportMode);

                importResults.add(sourcePath, importResult);
            }
            result.addProperty("success", true);
            result.add("details", importResults);
        } catch (Exception e) {
            logger.error("Error importing tags from config: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Failed to import tags from config: " + e.getMessage());
        }
        return result.toString();
    }

    @Override
    public String getTagProviders() {
        JsonArray providersArray = new JsonArray();
        try {
            context.getTagManager().getTagProviders().forEach(provider -> {
                providersArray.add(provider.getName());
            });
        } catch (Exception e) {
            logger.error("Error getting tag providers: {}", e.getMessage(), e);
        }
        return providersArray.toString();
    }

    @Override
    public String saveTagConfig(String configJson) {
        JsonObject result = new JsonObject();
        Path configPath = Paths.get(TagCICDConstants.CONFIG_FILE_PATH);

        try {
            // Ensure parent directories exist
            Files.createDirectories(configPath.getParent());

            // Write the configuration file
            Files.write(configPath, configJson.getBytes());

            logger.info("Successfully saved tag configuration to {}", configPath);
            result.addProperty("success", true);
        } catch (Exception e) {
            logger.error("Error saving tag configuration: {}", e.getMessage(), e);
            result.addProperty("success", false);
            result.addProperty("error", "Failed to save tag configuration: " + e.getMessage());
        }

        return result.toString();
    }

    @Override
    public String getInstallDirectory() {
        return context.getSystemManager().getDataDir().toPath().toAbsolutePath().getParent().toString();
    }

    @Override
    public String exportTagsToJson(String provider, String baseTagPath, boolean recursive, boolean localPropsOnly) {
        try {
            logger.info("RPC exportTagsToJson called: provider={}, baseTagPath={}, recursive={}, localPropsOnly={}",
                    provider, baseTagPath, recursive, localPropsOnly);
            JsonObject result = TagExportUtilities.exportTagsToJson(context.getTagManager(), provider, baseTagPath, recursive, localPropsOnly);
            return result.toString();
        } catch (Exception e) {
            logger.error("Error exporting tags to JSON: {}", e.getMessage(), e);
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("error", "Failed to export tags to JSON: " + e.getMessage());
            return result.toString();
        }
    }

    @Override
    public String performSelectiveExport(String provider, String baseTagPath, String targetFilePath, String exportMode, boolean excludeUdtDefinitions, String configBaseTagPath) {
        try {
            logger.info("RPC performSelectiveExport called: provider={}, baseTagPath={}, targetFilePath={}, exportMode={}, configBaseTagPath={}",
                    provider, baseTagPath, targetFilePath, exportMode, configBaseTagPath);

            // Step 1: Get the JSON data for the selected portion
            JsonObject newData = TagExportUtilities.exportTagsToJson(context.getTagManager(), provider, baseTagPath, true, false);

            // Step 2: Handle structured mode differently (directory vs single file)
            if ("structuredByType".equals(exportMode)) {
                return performStructuredSelectiveExport(baseTagPath, targetFilePath, newData);
            }

            // Check if this is an individual tag export for single file mode
            boolean isIndividualTagExport = isSingleTag(newData);
            if (isIndividualTagExport) {
                // For individual tags, wrap the tag in a tags array structure
                JsonArray tagsArray = new JsonArray();
                tagsArray.add(newData);
                JsonObject wrappedData = new JsonObject();
                wrappedData.add("tags", tagsArray);
                newData = wrappedData;
            }

            // Step 2: Resolve target file path (for single file mode)
            String absoluteFilePath;
            File targetFile = new File(targetFilePath);
            if (targetFile.isAbsolute()) {
                absoluteFilePath = targetFilePath;
            } else {
                // Resolve relative paths against the gateway's install directory
                String gatewayDir = getInstallDirectory();
                absoluteFilePath = Paths.get(gatewayDir, targetFilePath).toString();
            }
            
            targetFile = new File(absoluteFilePath);
            logger.info("Resolved target file path: {}", absoluteFilePath);

            // Step 3: Load existing file if it exists
            JsonObject existingData;
            if (targetFile.exists()) {
                try {
                    String existingContent = new String(Files.readAllBytes(targetFile.toPath()));
                    existingData = gson.fromJson(existingContent, JsonObject.class);
                    logger.info("Loaded existing file with {} top-level properties", existingData.keySet().size());
                } catch (Exception e) {
                    logger.warn("Could not read existing file {}, creating new: {}", absoluteFilePath, e.getMessage());
                    existingData = new JsonObject();
                }
            } else {
                logger.info("Target file does not exist, creating new: {}", absoluteFilePath);
                existingData = new JsonObject();
                // Ensure parent directories exist
                if (targetFile.getParentFile() != null) {
                    targetFile.getParentFile().mkdirs();
                }
            }

            // Step 4: Merge the new data into existing data
            logger.info("Merging data: selectedPath='{}', newData has {} tags, existingData has {} top-level keys", 
                       baseTagPath, 
                       newData.has("tags") ? newData.getAsJsonArray("tags").size() : 0,
                       existingData.keySet().size());
            JsonObject mergedData = mergeSelectiveData(existingData, newData, baseTagPath, exportMode, configBaseTagPath);
            logger.info("After merge: result has {} tags", 
                       mergedData.has("tags") ? mergedData.getAsJsonArray("tags").size() : 0);

            // Step 4.5: Ensure proper root structure (name and tagType)
            if ("singleFile".equals(exportMode)) {
                // For single file mode, use configBaseTagPath to determine the correct root structure
                ensureRootStructure(mergedData, configBaseTagPath);
            } else {
                // For other modes, use baseTagPath as before
                ensureRootStructure(mergedData, baseTagPath);
            }

            // Step 5: Write merged data back to file using FileUtilities for pretty printing
            FileUtilities.saveJsonToFile(mergedData, targetFile.getAbsolutePath());

            logger.info("Successfully wrote selective export to: {}", absoluteFilePath);

            // Step 6: Return success result
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Selective export completed successfully");
            result.addProperty("filePath", absoluteFilePath);
            return result.toString();

        } catch (Exception e) {
            logger.error("Selective export failed: {}", e.getMessage(), e);
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("error", "Selective export failed: " + e.getMessage());
            return result.toString();
        }
    }

    private JsonObject mergeSelectiveData(JsonObject existingData, JsonObject newData, String selectedPath, String exportMode, String configBaseTagPath) {
        if ("singleFile".equals(exportMode)) {
            return mergeSingleFileData(existingData, newData, selectedPath, configBaseTagPath);
        } else if ("structuredByType".equals(exportMode)) {
            return mergeStructuredData(existingData, newData, selectedPath);
        }
        return newData; // Fallback
    }

    private JsonObject mergeSingleFileData(JsonObject existingData, JsonObject newData, String selectedPath, String configBaseTagPath) {
        // Check if this is an empty file before we modify existingData
        boolean isEmptyFile = existingData.keySet().isEmpty();

        // For single file mode, we need to update the nested structure
        if (!existingData.has("tags")) {
            existingData.add("tags", new JsonArray());
        }

        JsonArray existingTags = existingData.getAsJsonArray("tags");
        JsonArray newTags = newData.has("tags") ? newData.getAsJsonArray("tags") : new JsonArray();

        logger.info("mergeSingleFileData: selectedPath='{}', existing tags={}, new tags={}", 
                   selectedPath, existingTags.size(), newTags.size());

        // If selectedPath is empty, replace everything
        if (selectedPath.isEmpty()) {
            JsonObject result = new JsonObject();
            result.add("tags", newTags);

            // Copy other properties from existing data
            for (String key : existingData.keySet()) {
                if (!"tags".equals(key)) {
                    result.add(key, existingData.get(key));
                }
            }
            return result;
        }

        // Special case: when selectedPath matches configBaseTagPath exactly,
        // we want the root to BE this folder, not contain it
        if (selectedPath.equals(configBaseTagPath) && isEmptyFile) {
            logger.debug("Empty file with selectedPath '{}' matching configBaseTagPath - replacing entire root structure", selectedPath);
            // Return the new data directly, letting ensureRootStructure set the root properties
            JsonObject result = new JsonObject();
            result.add("tags", newTags);
            return result;
        }

        // Only treat as target folder if existingData.name matches the LAST part of selectedPath
        String[] pathParts = selectedPath.split("/");
        String targetFolderName = pathParts[pathParts.length - 1]; // Get the last part
        boolean isTargetFolder = existingData.has("name") &&
                                existingData.get("name").getAsString().equals(targetFolderName);

        logger.debug("Path analysis: selectedPath='{}', targetFolderName='{}', existingData.name='{}', isTargetFolder={}",
                   selectedPath,
                   targetFolderName,
                   existingData.has("name") ? existingData.get("name").getAsString() : "null",
                   isTargetFolder);

        if (isTargetFolder) {
            // We are the target folder - replace our children with the new data
            logger.debug("We are the target folder, replacing children with new data");

            // Create a deep copy of existing data to preserve all properties
            JsonObject result = gson.fromJson(gson.toJson(existingData), JsonObject.class);

            // Replace only the tags array with new data
            result.add("tags", newTags);

            logger.debug("Preserved root structure: name='{}', tagType='{}'",
                       result.has("name") ? result.get("name").getAsString() : "null",
                       result.has("tagType") ? result.get("tagType").getAsString() : "null");

            return result;
        } else {
            // Find and update the specific folder/tag that matches selectedPath
            // If existingData represents a folder in the path, adjust the search path
            String searchPath = selectedPath;
            if (existingData.has("name")) {
                String currentFolderName = existingData.get("name").getAsString();
                String pathWithSlash = "/" + currentFolderName + "/";

                // Check if this folder appears in the selected path
                int folderIndex = selectedPath.indexOf(pathWithSlash);
                if (folderIndex >= 0) {
                    // Extract the path after this folder
                    searchPath = selectedPath.substring(folderIndex + pathWithSlash.length());
                    logger.debug("Adjusted search path from '{}' to '{}' since we're inside folder '{}'",
                               selectedPath, searchPath, currentFolderName);
                } else if (selectedPath.startsWith(currentFolderName + "/")) {
                    // Handle case where we're at the root
                    searchPath = selectedPath.substring(currentFolderName.length() + 1);
                    logger.debug("Adjusted search path from '{}' to '{}' since we're at root folder '{}'",
                               selectedPath, searchPath, currentFolderName);
                }
            } else if (isEmptyFile && selectedPath.contains("/")) {
                // For empty files, check if this is a provider-level export
                boolean isProviderLevelExport = configBaseTagPath.isEmpty();

                if (isProviderLevelExport) {
                    // Provider-level export: preserve the full path structure
                    logger.debug("Empty existing data (provider-level): preserving full search path '{}'", selectedPath);
                } else {
                    // Non-provider-level export: adjust path to avoid duplication
                    String[] selectedPathParts = selectedPath.split("/");
                    if (selectedPathParts.length > 1) {
                        searchPath = selectedPath.substring(selectedPathParts[0].length() + 1);
                        logger.debug("Empty existing data (folder-level): adjusted search path from '{}' to '{}' (root will be '{}')",
                                   selectedPath, searchPath, selectedPathParts[0]);
                    }
                }
            }

            logger.debug("About to call updateNestedStructure with searchPath='{}', newTags.size()={}", searchPath, newTags.size());
            boolean updated = updateNestedStructure(existingTags, searchPath, newTags);
            logger.debug("updateNestedStructure returned: {}, existingTags.size() is now: {}", updated, existingTags.size());

            if (!updated) {
                logger.warn("Could not find tag/folder '{}' in existing structure, attempting to build folder structure", searchPath);
                // Try to build the missing folder structure and place the tag
                boolean built = buildAndPlaceNestedStructure(existingTags, searchPath, newTags);
                if (!built) {
                    logger.error("Failed to build folder structure for path '{}', cannot place tag properly", searchPath);
                    throw new RuntimeException("Failed to build required folder structure for path: " + searchPath);
                }
                logger.info("Successfully built folder structure and placed tag for path: {}", searchPath);
            }
        }

        // Create result with proper property ordering
        JsonObject result = new JsonObject();

        // First, copy all non-tags properties from existing data to maintain order
        for (String key : existingData.keySet()) {
            if (!"tags".equals(key)) {
                result.add(key, existingData.get(key));
            }
        }

        // Then add the updated tags array
        result.add("tags", existingTags);

        return result;
    }
    
    /**
     * Recursively finds and updates the nested tag structure for the selected path
     */
    private boolean updateNestedStructure(JsonArray tagsArray, String targetPath, JsonArray newData) {
        String[] pathParts = targetPath.split("/");
        String currentName = pathParts[0];
        
        logger.info("Looking for tag/folder named '{}' in structure with {} items", currentName, tagsArray.size());
        
        // Find the tag/folder with the matching name
        for (int i = 0; i < tagsArray.size(); i++) {
            JsonElement element = tagsArray.get(i);
            if (!element.isJsonObject()) continue;
            
            JsonObject tag = element.getAsJsonObject();
            String tagName = tag.has("name") ? tag.get("name").getAsString() : "";
            
            logger.debug("Checking tag: name='{}', type='{}'", tagName, 
                        tag.has("tagType") ? tag.get("tagType").getAsString() : "unknown");
            
            if (tagName.equals(currentName)) {
                logger.info("Found matching tag: '{}'", tagName);
                
                if (pathParts.length == 1) {
                    // This is the target - update its children, not replace the whole object
                    String tagType = tag.has("tagType") ? tag.get("tagType").getAsString() : "";
                    
                    if ("Folder".equals(tagType)) {
                        // For folders, replace the children (tags array) with new data
                        tag.add("tags", newData);
                        logger.info("Updated folder '{}' children with {} new tags", tagName, newData.size());
                    } else {
                        // For individual tags, replace the entire tag object
                        tagsArray.set(i, newData.get(0));
                        logger.info("Replaced individual tag '{}' with new data", tagName);
                    }
                    return true;
                } else {
                    // This is an intermediate folder - recurse deeper
                    String remainingPath = String.join("/", 
                        java.util.Arrays.copyOfRange(pathParts, 1, pathParts.length));
                    
                    if (tag.has("tags") && tag.get("tags").isJsonArray()) {
                        JsonArray childTags = tag.getAsJsonArray("tags");
                        return updateNestedStructure(childTags, remainingPath, newData);
                    } else {
                        logger.warn("Tag '{}' doesn't have children but path continues: '{}'", tagName, remainingPath);
                        return false;
                    }
                }
            }
        }
        
        logger.warn("Could not find tag/folder named '{}' in current level", currentName);
        return false;
    }

    /**
     * Builds the missing folder structure and places the tag at the correct location
     */
    private boolean buildAndPlaceNestedStructure(JsonArray tagsArray, String targetPath, JsonArray newData) {
        String[] pathParts = targetPath.split("/");

        logger.debug("Building nested structure for path: '{}' with {} parts", targetPath, pathParts.length);

        JsonArray currentLevel = tagsArray;

        // Determine if the final element is a folder by checking the tagType
        boolean finalElementIsFolder = false;
        if (newData.size() > 0) {
            JsonElement firstElement = newData.get(0);
            if (firstElement.isJsonObject()) {
                JsonObject obj = firstElement.getAsJsonObject();
                String tagType = obj.has("tagType") ? obj.get("tagType").getAsString() : "";
                finalElementIsFolder = "Folder".equals(tagType);
            }
        }

        // Special case: for multi-part paths, if we have multiple items,
        // treat the final element as a folder that should contain these items
        // But NOT for single UDT definitions or single tags - those should be placed directly
        if (!finalElementIsFolder && pathParts.length > 1 && newData.size() > 1) {
            finalElementIsFolder = true;
            logger.debug("Multi-part path '{}' with {} elements - treating final element as folder container", targetPath, newData.size());
        }

        logger.debug("Final element '{}' is folder: {}", pathParts[pathParts.length - 1], finalElementIsFolder);

        // Build folder structure for all parts except the last one (if it's not a folder)
        int folderPartsCount = finalElementIsFolder ? pathParts.length : pathParts.length - 1;
        for (int i = 0; i < folderPartsCount; i++) {
            String folderName = pathParts[i];
            logger.debug("Looking for or creating folder: '{}'", folderName);

            // Look for existing folder
            JsonObject existingFolder = null;
            for (JsonElement element : currentLevel) {
                if (element.isJsonObject()) {
                    JsonObject folder = element.getAsJsonObject();
                    String name = folder.has("name") ? folder.get("name").getAsString() : "";
                    String tagType = folder.has("tagType") ? folder.get("tagType").getAsString() : "";

                    if (name.equals(folderName) && "Folder".equals(tagType)) {
                        existingFolder = folder;
                        logger.debug("Found existing folder: '{}'", folderName);
                        break;
                    }
                }
            }

            // Create folder if it doesn't exist
            if (existingFolder == null) {
                logger.debug("existingFolder = {}", existingFolder);
                logger.debug("Creating new folder: '{}'", folderName);
                existingFolder = new JsonObject();
                existingFolder.addProperty("name", folderName);
                existingFolder.addProperty("tagType", "Folder");
                existingFolder.add("tags", new JsonArray());
                currentLevel.add(existingFolder);
            }

            // Move to the next level (inside this folder)
            if (existingFolder.has("tags") && existingFolder.get("tags").isJsonArray()) {
                currentLevel = existingFolder.getAsJsonArray("tags");
            } else {
                logger.error("Folder '{}' doesn't have a tags array", folderName);
                return false;
            }
        }

        // Now place the tag(s) at the final location
        String finalTagName = pathParts[pathParts.length - 1];
        logger.debug("Placing tag(s) with final name: '{}'", finalTagName);

        // Remove any existing tag with the same name
        JsonArray updatedLevel = new JsonArray();
        for (JsonElement element : currentLevel) {
            if (element.isJsonObject()) {
                JsonObject tag = element.getAsJsonObject();
                String existingName = tag.has("name") ? tag.get("name").getAsString() : "";
                if (!existingName.equals(finalTagName)) {
                    updatedLevel.add(tag);
                }
            }
        }

        // Add the new tag(s)
        for (JsonElement element : newData) {
            updatedLevel.add(element);
        }

        // Replace the current level with the updated one
        currentLevel.getAsJsonArray(); // This doesn't actually do anything, we need to update the parent

        // We need a different approach - let's clear and rebuild the current level
        while (currentLevel.size() > 0) {
            currentLevel.remove(0);
        }
        for (JsonElement element : updatedLevel) {
            currentLevel.add(element);
        }

        logger.info("Successfully built structure and placed {} tag(s) at path: {}", newData.size(), targetPath);
        return true;
    }

    /**
     * Ensures the root JSON object has the proper name and tagType structure
     */
    private void ensureRootStructure(JsonObject rootData, String baseTagPath) {
        if (baseTagPath.isEmpty()) {
            // Root level - should be Provider
            rootData.addProperty("name", "");
            rootData.addProperty("tagType", "Provider");
            logger.info("Set root structure as Provider (empty baseTagPath)");
        } else {
            // Extract the root folder name from baseTagPath
            String[] pathParts = baseTagPath.split("/");
            String rootFolderName = pathParts[pathParts.length - 1];
            rootData.addProperty("name", rootFolderName);
            rootData.addProperty("tagType", "Folder");
            logger.info("Set root structure as Folder with name: '{}'", rootFolderName);
        }
    }

    private JsonObject mergeStructuredData(JsonObject existingData, JsonObject newData, String selectedPath) {
        // For structured mode, merge both tags and udts sections
        JsonObject result = gson.fromJson(gson.toJson(existingData), JsonObject.class);

        // Merge tags
        if (newData.has("tags")) {
            if (!result.has("tags")) {
                result.add("tags", new JsonArray());
            }

            JsonArray existingTags = result.getAsJsonArray("tags");
            JsonArray newTags = newData.getAsJsonArray("tags");

            // Filter out existing tags for the selected path
            JsonArray filteredTags = new JsonArray();
            for (JsonElement element : existingTags) {
                JsonObject tag = element.getAsJsonObject();
                String tagPath = tag.has("path") ? tag.get("path").getAsString() : "";

                if (selectedPath.isEmpty() || !tagPath.startsWith(selectedPath)) {
                    filteredTags.add(tag);
                }
            }

            // Add new tags
            for (JsonElement element : newTags) {
                filteredTags.add(element);
            }

            result.add("tags", filteredTags);
        }

        // Merge udts
        if (newData.has("udts")) {
            if (!result.has("udts")) {
                result.add("udts", new JsonArray());
            }

            JsonArray existingUdts = result.getAsJsonArray("udts");
            JsonArray newUdts = newData.getAsJsonArray("udts");

            // Filter out existing UDTs for the selected path
            JsonArray filteredUdts = new JsonArray();
            for (JsonElement element : existingUdts) {
                JsonObject udt = element.getAsJsonObject();
                String udtPath = udt.has("path") ? udt.get("path").getAsString() : "";

                if (selectedPath.isEmpty() || !udtPath.startsWith(selectedPath)) {
                    filteredUdts.add(udt);
                }
            }

            // Add new UDTs
            for (JsonElement element : newUdts) {
                filteredUdts.add(element);
            }

            result.add("udts", filteredUdts);
        }

        return result;
    }
    
    private String performStructuredSelectiveExport(String baseTagPath, String targetDirPath, JsonObject newData) {
        try {
            logger.info("Performing structured selective export: baseTagPath='{}', targetDirPath='{}'", baseTagPath, targetDirPath);
            
            // Resolve target directory path
            String absoluteDirPath;
            File targetDir = new File(targetDirPath);
            if (targetDir.isAbsolute()) {
                absoluteDirPath = targetDirPath;
            } else {
                String gatewayDir = getInstallDirectory();
                absoluteDirPath = Paths.get(gatewayDir, targetDirPath).toString();
            }
            
            targetDir = new File(absoluteDirPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            logger.info("Resolved target directory: {}", absoluteDirPath);
            
            // For structured mode, we need to determine which files to update based on the path
            if (baseTagPath.isEmpty()) {
                // Root level - update both tags.json and udts.json
                return updateStructuredFiles(targetDir, newData, "");
            } else {
                // For selective export, the dialog has already calculated the correct target path
                // We don't need to create additional subdirectories based on baseTagPath
                logger.info("Using target directory as provided by dialog: {}", targetDir.getAbsolutePath());
                return updateStructuredFiles(targetDir, newData, baseTagPath);
            }
            
        } catch (Exception e) {
            logger.error("Structured selective export failed: {}", e.getMessage(), e);
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("error", "Structured selective export failed: " + e.getMessage());
            return result.toString();
        }
    }
    
    private String updateStructuredFiles(File targetDir, JsonObject newData, String selectedPath) throws IOException {
        logger.info("Updating structured files in: {} using proper structured format", targetDir.getAbsolutePath());

        // Check if this is a single tag export (AtomicTag, UdtInstance, or UdtType)
        boolean isIndividualTagExport = isSingleTag(newData);

        if (isIndividualTagExport) {
            // Handle single tag case - add to appropriate file (tags.json or udts.json)
            String tagType = newData.get("tagType").getAsString();
            String tagName = newData.get("name").getAsString();

            logger.info("Processing individual tag export: '{}' (type: {})", tagName, tagType);

            // For individual tag exports, the target directory path includes the tag name at the end
            // We need to use the parent directory to update the appropriate structured file
            String actualTargetDir = targetDir.getAbsolutePath();
            File emptyDirToDelete = null;
            if (actualTargetDir.endsWith(tagName)) {
                emptyDirToDelete = targetDir; // Remember the empty directory to clean up
                actualTargetDir = actualTargetDir.substring(0, actualTargetDir.length() - tagName.length());
                if (actualTargetDir.endsWith("/") || actualTargetDir.endsWith("\\")) {
                    actualTargetDir = actualTargetDir.substring(0, actualTargetDir.length() - 1);
                }
                logger.info("Adjusted target directory from '{}' to '{}' for individual tag export",
                           targetDir.getAbsolutePath(), actualTargetDir);
            }

            if ("UdtInstance".equals(tagType)) {
                // Add to udts.json
                updateStructuredFile(actualTargetDir, "udts.json", newData, tagName);
            } else {
                // Add to tags.json (AtomicTag, UdtType, or other tag types)
                updateStructuredFile(actualTargetDir, "tags.json", newData, tagName);
            }

            // Clean up the empty directory that was created for the individual tag
            if (emptyDirToDelete != null && emptyDirToDelete.exists() && emptyDirToDelete.isDirectory()) {
                String[] contents = emptyDirToDelete.list();
                if (contents != null && contents.length == 0) {
                    if (emptyDirToDelete.delete()) {
                        logger.info("Cleaned up empty directory: {}", emptyDirToDelete.getAbsolutePath());
                    } else {
                        logger.warn("Failed to delete empty directory: {}", emptyDirToDelete.getAbsolutePath());
                    }
                } else {
                    logger.debug("Directory not empty, skipping cleanup: {}", emptyDirToDelete.getAbsolutePath());
                }
            }
        } else {
            // Process the JSON data using structured export logic for folder/container exports
            if (newData.has("tags") && newData.getAsJsonArray("tags").size() > 0) {
                exportTagsInStructuredFormat(newData, targetDir.getAbsolutePath());
                logger.info("Processed {} tags in structured format", newData.getAsJsonArray("tags").size());
            }
        }
        
        // Return success
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "Structured selective export completed successfully");
        result.addProperty("targetDirectory", targetDir.getAbsolutePath());
        return result.toString();
    }

    /**
     * Recursively exports tags in structured format, similar to StructuredFilesExportStrategy
     */
    private void exportTagsInStructuredFormat(JsonObject json, String basePath) throws IOException {
        if (!json.has("tags")) {
            return;
        }

        JsonArray tagsArray = json.getAsJsonArray("tags");
        JsonArray regularTags = new JsonArray();
        JsonArray udtInstances = new JsonArray();

        for (JsonElement tagElement : tagsArray) {
            if (!tagElement.isJsonObject()) {
                continue;
            }

            JsonObject tagObject = tagElement.getAsJsonObject();
            String tagType = tagObject.has("tagType") ? tagObject.get("tagType").getAsString() : "";
            String tagName = tagObject.has("name") ? tagObject.get("name").getAsString() : "";

            // Handle _types_ folder specially for UDT definitions
            if ("_types_".equals(tagName) && "Folder".equals(tagType)) {
                String typesPath = basePath + "/_types_";
                File typesDir = new File(typesPath);
                typesDir.mkdirs();

                // Export UDT definitions to the _types_ folder
                JsonArray udtDefinitions = new JsonArray();
                if (tagObject.has("tags")) {
                    JsonArray typesTags = tagObject.getAsJsonArray("tags");
                    for (JsonElement typeElement : typesTags) {
                        udtDefinitions.add(typeElement);
                    }
                }

                if (udtDefinitions.size() > 0) {
                    JsonObject udtDefinitionsJson = new JsonObject();
                    udtDefinitionsJson.add("tags", udtDefinitions);
                    FileUtilities.saveJsonToFile(udtDefinitionsJson, typesPath + "/udts.json");
                    logger.info("Exported {} UDT definitions to {}/udts.json", udtDefinitions.size(), typesPath);
                }
                continue;
            }

            // Process regular folder
            if ("Folder".equals(tagType)) {
                String folderPath = basePath + "/" + tagName;
                File folder = new File(folderPath);
                folder.mkdirs();
                exportTagsInStructuredFormat(tagObject, folderPath);
                logger.info("Created folder and processed contents: {}", folderPath);
            }
            // Process UDT instances and UDT types (both go to udts.json)
            else if ("UdtInstance".equals(tagType) || "UdtType".equals(tagType)) {
                udtInstances.add(tagObject);
            }
            // Process regular tags
            else {
                regularTags.add(tagObject);
            }
        }

        // Save regularTags to tags.json if there are any
        if (regularTags.size() > 0) {
            JsonObject tagsJson = new JsonObject();
            tagsJson.add("tags", regularTags);
            FileUtilities.saveJsonToFile(tagsJson, basePath + "/tags.json");
            logger.info("Exported {} regular tags to {}/tags.json", regularTags.size(), basePath);
        }

        // Save UDT instances and UDT types to udts.json if there are any
        if (udtInstances.size() > 0) {
            JsonObject udtsJson = new JsonObject();
            udtsJson.add("tags", udtInstances);
            FileUtilities.saveJsonToFile(udtsJson, basePath + "/udts.json");
            logger.info("Exported {} UDT instances/types to {}/udts.json", udtInstances.size(), basePath);
        }
    }

    /**
     * Determines if the given JSON object represents a single tag (AtomicTag, UdtInstance, or UdtType)
     * rather than a container/folder structure.
     *
     * @param json the JSON object to check
     * @return true if it represents a single tag, false otherwise
     */
    private boolean isSingleTag(JsonObject json) {
        return json.has("tagType") && json.has("name") &&
               ("AtomicTag".equals(json.get("tagType").getAsString()) ||
                "UdtInstance".equals(json.get("tagType").getAsString()) ||
                "UdtType".equals(json.get("tagType").getAsString()));
    }

    /**
     * Updates a specific structured file (tags.json or udts.json) by merging in a new tag
     *
     * @param basePath the directory path containing the file
     * @param fileName the file name (tags.json or udts.json)
     * @param newTag the new tag to add
     * @param tagName the name of the tag being added
     */
    private void updateStructuredFile(String basePath, String fileName, JsonObject newTag, String tagName) throws IOException {
        logger.info("Updating structured file: {}/{} with tag '{}'", basePath, fileName, tagName);
        File targetFile = new File(basePath, fileName);
        JsonObject fileData;

        // Load existing file or create new structure
        if (targetFile.exists()) {
            try {
                String existingContent = new String(Files.readAllBytes(targetFile.toPath()));
                fileData = gson.fromJson(existingContent, JsonObject.class);
                logger.info("Loaded existing {} with {} tags", fileName,
                           fileData.has("tags") ? fileData.getAsJsonArray("tags").size() : 0);
            } catch (Exception e) {
                logger.warn("Could not read existing file {}, creating new: {}", targetFile.getAbsolutePath(), e.getMessage());
                fileData = new JsonObject();
                fileData.add("tags", new JsonArray());
            }
        } else {
            logger.info("Creating new {} file", fileName);
            fileData = new JsonObject();
            fileData.add("tags", new JsonArray());
        }

        // Ensure tags array exists
        if (!fileData.has("tags")) {
            fileData.add("tags", new JsonArray());
        }

        JsonArray tagsArray = fileData.getAsJsonArray("tags");

        // Remove existing tag with the same name if it exists
        JsonArray updatedTags = new JsonArray();
        for (JsonElement element : tagsArray) {
            if (element.isJsonObject()) {
                JsonObject existingTag = element.getAsJsonObject();
                String existingTagName = existingTag.has("name") ? existingTag.get("name").getAsString() : "";
                if (!existingTagName.equals(tagName)) {
                    updatedTags.add(existingTag);
                }
            }
        }

        // Add the new tag
        updatedTags.add(newTag);
        fileData.add("tags", updatedTags);

        // Write the updated file using FileUtilities for pretty printing
        FileUtilities.saveJsonToFile(fileData, targetFile.getAbsolutePath());

        logger.info("Updated {} with tag '{}' - file now contains {} tags",
                   fileName, tagName, updatedTags.size());
    }
}