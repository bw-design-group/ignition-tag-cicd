/*
 * Copyright 2023 Barry-Wehmiller Design Group
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package dev.bwdesigngroup.ignition.tag_cicd.common.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.BasicTagPath;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;

/**
 * A utility class for tag configuration operations.
 *
 * @author Keith Gamble
 */
public class TagConfigUtilities {
	private static final Logger logger = LoggerFactory.getLogger(TagConfigUtilities.class.getName());

	public static final String DEFAULT_PROVIDER = "default";
	public static final String UDT_TYPES_FOLDER = "_types_";

	/**
	 * Returns a tag configuration model for the given provider and tag path.
	 *
	 * @param provider       the provider to retrieve tag configuration for.
	 * @param tagPath        the base tag path to retrieve tag configuration for.
	 * @param recursive      If true, will recursively search the `baseTagPath` for
	 *                       tags. If false, will only search for the direct
	 *                       children of `baseTagPath` for tags.
	 * @param localPropsOnly Set to True to only return configuration created by a
	 *                       user (aka no inherited properties). Useful for tag
	 *                       export and tag UI edits of raw JSON text.
	 * @return a tag configuration model for the given provider and tag path.
	 */
	public static TagConfigurationModel getTagConfigurationModel(GatewayTagManager tagManager, String provider,
			String tagPath, Boolean recursive, Boolean localPropsOnly) {
		TagPath baseTagPath;
		if (tagPath == null || tagPath.isEmpty()) {
			baseTagPath = new BasicTagPath(provider);
		} else {
			// Properly split the tag path into components
			List<String> pathComponents = new ArrayList<>();
			String[] parts = tagPath.split("/");
			for (String part : parts) {
				if (part != null && !part.trim().isEmpty()) {
					pathComponents.add(part.trim());
				}
			}
			baseTagPath = new BasicTagPath(provider, pathComponents);
		}

		logger.info("🔍 DETAILED: Requesting tag configuration for provider '" + provider + "' and tag path '" + baseTagPath.toString()
				+ "' with recursive=" + recursive + " and localPropsOnly=" + localPropsOnly);

		try {
			// Step 1: Get the tag provider
			logger.info("🔍 DETAILED: Step 1 - Getting tag provider for '" + provider + "'");
			var tagProvider = tagManager.getTagProvider(provider);
			if (tagProvider == null) {
				logger.info("🔍 DETAILED: Step 1 FAILED - Tag provider '" + provider + "' returned null");
				return null;
			}
			logger.info("🔍 DETAILED: Step 1 SUCCESS - Tag provider '" + provider + "' obtained: " + tagProvider.getClass().getSimpleName());

			// Step 2: Call getTagConfigsAsync
			logger.info("🔍 DETAILED: Step 2 - Calling getTagConfigsAsync for path: " + baseTagPath.toString());
			var asyncResult = tagProvider.getTagConfigsAsync(List.of(baseTagPath), recursive, localPropsOnly);
			if (asyncResult == null) {
				logger.info("🔍 DETAILED: Step 2 FAILED - getTagConfigsAsync returned null CompletableFuture");
				return null;
			}
			logger.info("🔍 DETAILED: Step 2 SUCCESS - getTagConfigsAsync returned CompletableFuture");

			// Step 3: Join the async result
			logger.info("🔍 DETAILED: Step 3 - Joining async result");
			var configList = asyncResult.join();
			if (configList == null) {
				logger.info("🔍 DETAILED: Step 3 FAILED - join() returned null list");
				return null;
			}
			logger.info("🔍 DETAILED: Step 3 SUCCESS - join() returned list with size: " + configList.size());

			// Step 4: Get the first element
			if (configList.isEmpty()) {
				logger.info("🔍 DETAILED: Step 4 FAILED - config list is empty");
				return null;
			}
			
			TagConfigurationModel tagConfigurationModel = configList.get(0);
			if (tagConfigurationModel == null) {
				logger.info("🔍 DETAILED: Step 4 FAILED - first element in list is null");
				return null;
			}
			logger.info("🔍 DETAILED: Step 4 SUCCESS - got TagConfigurationModel: " + tagConfigurationModel.getClass().getSimpleName());

			// Step 5: Check the model details
			logger.info("🔍 DETAILED: Step 5 - TagConfigurationModel details:");
			logger.info("🔍 DETAILED:   - Class: " + tagConfigurationModel.getClass().getName());
			logger.info("🔍 DETAILED:   - Name: " + tagConfigurationModel.getName());
			logger.info("🔍 DETAILED:   - Path: " + tagConfigurationModel.getPath());
			logger.info("🔍 DETAILED:   - Type: " + tagConfigurationModel.getType());
			
			// Try different ways to access children
			logger.info("🔍 DETAILED: Attempting to access children...");
			
			try {
				var children = tagConfigurationModel.getChildren();
				if (children == null) {
					logger.info("🔍 DETAILED:   - getChildren() returned: NULL");
				} else {
					logger.info("🔍 DETAILED:   - getChildren() returned: list with " + children.size() + " items");
					logger.info("🔍 DETAILED:   - Children list class: " + children.getClass().getName());
					
					if (!children.isEmpty()) {
						logger.info("🔍 DETAILED:   - First few children:");
						for (int i = 0; i < Math.min(5, children.size()); i++) {
							var child = children.get(i);
							logger.info("🔍 DETAILED:     [" + i + "] '" + child.getName() + "' (class: " + child.getClass().getSimpleName() + ")");
						}
					} else {
						logger.info("🔍 DETAILED:   - Children list is empty");
						
						// Try to see if there are other methods that might give us more info
						logger.info("🔍 DETAILED: Checking if there are alternative ways to detect children...");
						
						// Check if the model has any properties that might indicate children exist
						try {
							var properties = tagConfigurationModel.getProperties();
							logger.info("🔍 DETAILED:   - Properties count: " + (properties != null ? properties.size() : "NULL"));
						} catch (Exception e) {
							logger.info("🔍 DETAILED:   - Could not get properties: " + e.getMessage());
						}
						
						// Let's also check what happens if we try to query this exact path using browse API approach
						logger.info("🔍 DETAILED: Alternative: let's check what the TagProvider says directly...");
						try {
							// Instead of using getTagConfigsAsync, let's see what other methods are available
							logger.info("🔍 DETAILED:   - TagProvider class: " + tagProvider.getClass().getName());
							
						} catch (Exception altEx) {
							logger.info("🔍 DETAILED:   - Alternative check failed: " + altEx.getMessage());
						}
					}
				}
			} catch (Exception childEx) {
				logger.info("🔍 DETAILED: EXCEPTION getting children: " + childEx.getClass().getSimpleName() + " - " + childEx.getMessage());
				logger.info("🔍 DETAILED: Children access stack trace: ", childEx);
			}

			logger.info("🔍 DETAILED: SUCCESS - Returning TagConfigurationModel");
			return tagConfigurationModel;

		} catch (Exception e) {
			logger.info("🔍 DETAILED: EXCEPTION in getTagConfigurationModel: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			logger.info("🔍 DETAILED: Stack trace: ", e);
			return null;
		}
	}

	/**
	 * Checks if tags exist in the given provider and tag path.
	 * 
	 * @param tagManager the GatewayTagManager to use for checking tags
	 * @param provider the provider to check for tags
	 * @param tagPath the base tag path to check for existing tags
	 * @return true if tags exist in the specified path, false otherwise
	 */
	public static boolean tagsExistInPath(GatewayTagManager tagManager, String provider, String tagPath) {
		try {
			logger.info("🔍 DETAILED: === STARTING tagsExistInPath for provider '" + provider + "' path '" + tagPath + "' ===");
			
			try {
				// Test if we can access the tag provider at all
				logger.info("🔍 DETAILED: Testing tag provider access for '" + provider + "'");
				var tagProvider = tagManager.getTagProvider(provider);
				if (tagProvider == null) {
					logger.info("🔍 DETAILED: Tag provider '" + provider + "' not found - RETURNING FALSE");
					return false;
				}
				logger.info("🔍 DETAILED: Tag provider '" + provider + "' found successfully");
				
				// Try to get tag configuration with different parameter combinations
				logger.info("🔍 DETAILED: About to call getTagConfigurationModel...");
				
				// Use recursive=true to properly load children - this was the missing piece!
				logger.info("🔍 DETAILED: Calling getTagConfigurationModel with recursive=true to load children");
				TagConfigurationModel config = getTagConfigurationModel(tagManager, provider, tagPath, true, false);
				logger.info("🔍 DETAILED: getTagConfigurationModel(recursive=true) returned: " + (config == null ? "NULL" : "valid config"));
				
				if (config != null) {
					var children = config.getChildren();
					logger.info("🔍 DETAILED: config.getChildren() returned: " + (children == null ? "NULL" : "list with " + children.size() + " items"));
					
					if (children != null) {
						logger.info("🔍 DETAILED: Found " + children.size() + " total items in provider '" + provider + "' at path '" + tagPath + "'");
						
						// Log all children for debugging
						if (!children.isEmpty()) {
							logger.info("🔍 DETAILED: All children found:");
							for (int i = 0; i < children.size(); i++) {
								var child = children.get(i);
								boolean isTypesFolder = UDT_TYPES_FOLDER.equals(child.getName());
								logger.info("🔍 DETAILED:   [" + i + "] '" + child.getName() + 
										   "' (isTypesFolder: " + isTypesFolder + ")");
							}
						}
						
						// Different logic based on what path we're checking
						if (tagPath.isEmpty()) {
							// For root-level checks (baseTagPath=""), consider ANY meaningful content including _types_
							// But we need to check if _types_ actually has UDT definitions, not just the folder itself
							long nonTypesTagCount = children.stream()
								.filter(child -> !UDT_TYPES_FOLDER.equals(child.getName()))
								.count();
							
							boolean hasTypesWithContent = false;
							if (nonTypesTagCount == 0) {
								// Only _types_ folder exists, check if it has actual UDT definitions
								for (var child : children) {
									if (UDT_TYPES_FOLDER.equals(child.getName())) {
										// Check if _types_ folder has content
										try {
											TagConfigurationModel typesConfig = getTagConfigurationModel(tagManager, provider, UDT_TYPES_FOLDER, true, false);
											if (typesConfig != null && typesConfig.getChildren() != null && !typesConfig.getChildren().isEmpty()) {
												hasTypesWithContent = true;
												logger.info("🔍 DETAILED: _types_ folder contains " + typesConfig.getChildren().size() + " UDT definitions");
											}
										} catch (Exception e) {
											logger.info("🔍 DETAILED: Could not check _types_ content: " + e.getMessage());
										}
										break;
									}
								}
							}
							
							if (nonTypesTagCount > 0 || hasTypesWithContent) {
								logger.info("🔍 DETAILED: Provider '" + provider + "' has meaningful content - RETURNING TRUE (nonTypesCount=" + nonTypesTagCount + ", hasTypesWithContent=" + hasTypesWithContent + ")");
								return true;
							} else {
								logger.info("🔍 DETAILED: Provider '" + provider + "' has no meaningful content - RETURNING FALSE");
								return false;
							}
						} else {
							// For specific path checks (like baseTagPath="_types_"), just count direct children
							if (children.size() > 0) {
								logger.info("🔍 DETAILED: Path '" + tagPath + "' has " + children.size() + " items - RETURNING TRUE");
								return true;
							} else {
								logger.info("🔍 DETAILED: Path '" + tagPath + "' has no items - RETURNING FALSE");
								return false;
							}
						}
					} else {
						logger.info("🔍 DETAILED: config.getChildren() returned NULL - RETURNING FALSE");
						return false;
					}
				} else {
					logger.info("🔍 DETAILED: getTagConfigurationModel returned NULL - RETURNING FALSE (fail-safe)");
					return false; // Allow import when we can't detect (fail-safe)
				}
				
			} catch (Exception e) {
				logger.info("🔍 DETAILED: EXCEPTION in tagsExistInPath: " + e.getClass().getSimpleName() + " - " + e.getMessage() + " - RETURNING FALSE (fail-safe)");
				logger.info("🔍 DETAILED: Exception stack trace: ", e);
				return false; // Fail-safe: allow import when detection fails
			}
			
		} catch (Exception e) {
			logger.warn("🔍 DETAILED: OUTER EXCEPTION in tagsExistInPath: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			return false; // Fail-safe: allow import on errors
		} finally {
			logger.info("🔍 DETAILED: === ENDING tagsExistInPath ===");
		}
	}


	/**
	 * Deletes all tags in the given tag configuration model, and returns a list of quality codes for the deleted tags.
	 *
	 * @param provider the tag provider for the configuration model
	 * @param baseTagPath the base tag path for the tags to be deleted
	 * @param tagConfigurationModel the configuration model for the tags to be deleted
	 * @return a list of quality codes for the deleted tags
	 */
	public static List<QualityCode> deleteTagsInConfigurationModel(GatewayTagManager tagManager, String provider, TagPath baseTagPath, TagConfigurationModel tagConfigurationModel) {

		logger.trace("Deleting tags in configuration model for provider " + provider + " and tag path " + baseTagPath.toString() + " in configuration model: " + tagConfigurationModel.toString());

		List<TagConfigurationModel> configModelChildren = tagConfigurationModel.getChildren();
		List<TagPath> tagPaths =  new ArrayList<TagPath>();
		List<QualityCode> deleteResults = new ArrayList<QualityCode>();

		logger.info("Found " + configModelChildren.size() + " tags to delete from provider " + provider);

		for (TagConfigurationModel configModelChild : configModelChildren) {
			logger.info("Checking tag path " + configModelChild.getPath().toString() + " for _types_ folder");
			if (configModelChild.getPath().toString().equals(UDT_TYPES_FOLDER)) {
				// Delete every tag in the `_types_` folder
				logger.info("Found _types_ folder, deleting all tags in folder");
				deleteResults.addAll(deleteTagsInConfigurationModel(tagManager, provider, new BasicTagPath(provider, List.of(UDT_TYPES_FOLDER)), configModelChild));
				continue;
			}

			logger.info("Adding tag path " + configModelChild.getPath() + " to list of tags to delete");
			tagPaths.add(baseTagPath.getChildPath(configModelChild.getPath().toString()));
		}

		logger.info("Deleting tags from provider " + provider + " with paths: " + tagPaths.toString());
		deleteResults.addAll(tagManager.getTagProvider(provider).removeTagConfigsAsync(tagPaths).join());

		return deleteResults;
	}

	/**
	 * Converts a List of QualityCode objects to a JsonArray of QualityCode strings
	 *
	 * @param qualityCodes a List of QualityCode objects to convert to a JsonArray
	 * @return a JsonArray of QualityCode strings
	 */
	public static JsonArray convertQualityCodesToArray(List<QualityCode> qualityCodes) {
		JsonArray qualityCodesArray = new JsonArray();
		qualityCodes.forEach(code -> qualityCodesArray.add(code.toString()));
		return qualityCodesArray;
	}

	/**
	 * Adds a JsonObject of tags and their corresponding QualityCodes to the given JsonObject, 
	 * under the given key, if the JsonObject of tags is not empty.
	 *
	 * @param jsonObject the JsonObject to which the tags and QualityCodes should be added
	 * @param tags the JsonObject containing the tags and their corresponding QualityCodes
	 * @param key the key to use when adding the tags to the main JsonObject
	 */

	 public static void addQualityCodesToJsonObject(JsonObject jsonObject, JsonObject tags, String key) {
		if (tags.size() > 0) {
			jsonObject.add(key, tags);
		}
	}

	/**
     * Separates the UDT types from the regular tags in the given JsonObject,
     * sorts the UDT types based on their dependencies, and returns a new JsonObject
     * with the sorted UDT types and regular tags.
     *
     * @param tagsJson the JsonObject containing the tags and UDT types
     * @return a new JsonObject with the sorted UDT types and regular tags
     */
	public static JsonObject sortTagsAndUdtTypes(JsonObject tagsJson) {
		JsonObject sortedTagsJson = new JsonObject();
		JsonArray regularTags = new JsonArray();

		// Separate UDT types from regular tags and UDT instances
		Map<String, JsonArray> udtTypesMap = new HashMap<>();
		separateUdtTypesRegularTagsAndInstances(tagsJson, udtTypesMap, regularTags);

		// Sort the UDT types in each folder based on their dependencies
		for (Map.Entry<String, JsonArray> entry : udtTypesMap.entrySet()) {
			String folderPath = entry.getKey();
			JsonArray udtTypes = entry.getValue();
			JsonArray sortedUdtTypes = sortUdtTypes(udtTypes);

			// Create the folder structure for the sorted UDT types
			String[] folderNames = folderPath.split("/");
			JsonObject currentFolder = sortedTagsJson;
			for (String folderName : folderNames) {
				JsonArray tagsArray = currentFolder.getAsJsonArray("tags");
				if (tagsArray == null) {
					tagsArray = new JsonArray();
					currentFolder.add("tags", tagsArray);
				}
				JsonObject existingFolder = getTagByName(tagsArray, folderName);
				if (existingFolder == null) {
					JsonObject newFolder = new JsonObject();
					newFolder.addProperty("name", folderName);
					newFolder.addProperty("tagType", "Folder");
					newFolder.add("tags", new JsonArray());
					tagsArray.add(newFolder);
					currentFolder = newFolder;
				} else {
					currentFolder = existingFolder;
				}
			}
			currentFolder.getAsJsonArray("tags").addAll(sortedUdtTypes);
		}

		// Add the regular tags to the sortedTagsJson
		sortedTagsJson.add("tags", regularTags);

		return sortedTagsJson;
	}

    /**
     * Separates the UDT types from the regular tags in the given JsonObject
     * and adds them to the respective arrays.
     *
     * @param json        the JsonObject to separate tags from
     * @param udtTypesMap the array to store the UDT types
     * @param regularTags the array to store the regular tags
     */
	private static void separateUdtTypesRegularTagsAndInstances(JsonObject json, Map<String, JsonArray> udtTypesMap, JsonArray regularTags) {
		if (json.has("tags")) {
			JsonArray tags = json.getAsJsonArray("tags");
			for (JsonElement tagElement : tags) {
				JsonObject tag = tagElement.getAsJsonObject();
				String tagType = tag.get("tagType").getAsString();
				if (tagType.equals("UdtType")) {
					String folderPath = getTagFolderPath(json);
					if (!udtTypesMap.containsKey(folderPath)) {
						udtTypesMap.put(folderPath, new JsonArray());
					}
					udtTypesMap.get(folderPath).add(tag);
				} else if (tagType.equals("UdtInstance")) {
					// Recursively separate UDT instances and their children
					separateUdtTypesRegularTagsAndInstances(tag, udtTypesMap, new JsonArray());
					regularTags.add(tag);
				} else if (tagType.equals("Folder")) {
					separateUdtTypesRegularTagsAndInstances(tag, udtTypesMap, regularTags);
				} else {
					regularTags.add(tag);
				}
			}
		}
	}


	/**
	 * Returns the folder path of the given tag.
	 *
	 * @param tag the tag to get the folder path from
	 * @return the folder path of the given tag
	 */
	private static String getTagFolderPath(JsonObject json) {
		List<String> folderNames = new ArrayList<>();
		JsonObject currentFolder = json;
		while (currentFolder.has("name") && currentFolder.has("tagType") && currentFolder.get("tagType").getAsString().equals("Folder")) {
			folderNames.add(0, currentFolder.get("name").getAsString());
			if (currentFolder.has("tags")) {
				currentFolder = currentFolder.getAsJsonArray("tags").get(0).getAsJsonObject();
			} else {
				break;
			}
		}
		return String.join("/", folderNames);
	}

	/**
	 * Returns the tag with the given name from the given JsonArray.
	 *
	 * @param tags the JsonArray to search for the tag
	 * @param name the name of the tag to find
	 * @return the tag with the given name, or null if not found
	 */
	private static JsonObject getTagByName(JsonArray tags, String name) {
		if (tags != null) {
			for (JsonElement tagElement : tags) {
				JsonObject tag = tagElement.getAsJsonObject();
				if (tag.get("name").getAsString().equals(name)) {
					return tag;
				}
			}
		}
		return null;
	}

    /**
     * Sorts the UDT types in the given JsonArray based on their dependencies.
     *
     * @param udtTypesArray the JsonArray containing the UDT types
     * @return a sorted JsonArray of the UDT types
     */
    private static JsonArray sortUdtTypes(JsonArray udtTypesArray) {
        Map<String, JsonObject> udtTypesMap = new HashMap<>();
        Queue<JsonObject> udtTypesQueue = new ArrayDeque<>();

        // Extract UDT types from the array and add them to the map and queue
        for (JsonElement udtTypeElement : udtTypesArray) {
            JsonObject udtType = udtTypeElement.getAsJsonObject();
            String udtName = udtType.get("name").getAsString();
            udtTypesMap.put(udtName, udtType);
            udtTypesQueue.offer(udtType);
        }

        // Sort the UDT types based on their dependencies
        JsonArray sortedUdtTypes = new JsonArray();
        while (!udtTypesQueue.isEmpty()) {
            JsonObject udtType = udtTypesQueue.poll();
            sortUdtType(udtType, udtTypesMap, udtTypesQueue, sortedUdtTypes);
        }

        return sortedUdtTypes;
    }

    /**
     * Recursively sorts the dependencies of the given UDT type and adds it to the sortedUdtTypes array.
     *
     * @param udtType        the UDT type to sort
     * @param udtTypesMap    the map of UDT types by their name
     * @param udtTypesQueue  the queue of UDT types to process
     * @param sortedUdtTypes the array to store the sorted UDT types
     */
    private static void sortUdtType(JsonObject udtType, Map<String, JsonObject> udtTypesMap,
                                    Queue<JsonObject> udtTypesQueue, JsonArray sortedUdtTypes) {
        if (sortedUdtTypes.contains(udtType)) {
            return;
        }

        if (udtType.has("tags")) {
            JsonArray tags = udtType.getAsJsonArray("tags");
            for (JsonElement tagElement : tags) {
                JsonObject tag = tagElement.getAsJsonObject();
                String tagType = tag.get("tagType").getAsString();
                if (tagType.equals("UdtInstance")) {
                    String dependentUdtName = tag.get("typeId").getAsString();
                    JsonObject dependentUdtType = udtTypesMap.get(dependentUdtName);
                    if (dependentUdtType != null) {
                        sortUdtType(dependentUdtType, udtTypesMap, udtTypesQueue, sortedUdtTypes);
                    }
                }
            }
        }

        sortedUdtTypes.add(udtType);
    }
}
