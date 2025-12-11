package dev.bwdesigngroup.ignition.tag_cicd.designer.dialog;

import dev.bwdesigngroup.ignition.tag_cicd.common.TagCICDRPC;
import dev.bwdesigngroup.ignition.tag_cicd.common.constants.TagCICDConstants;
import dev.bwdesigngroup.ignition.tag_cicd.designer.model.TagConfigManager;
import dev.bwdesigngroup.ignition.tag_cicd.designer.util.DialogUtilities;
import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.tags.tree.selection.TagSelectionComponent;
import com.inductiveautomation.ignition.designer.tags.tree.selection.TagSelectionTreePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.inductiveautomation.ignition.common.BundleUtil.i18n;

public class SelectiveTagExportDialog extends JDialog {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DesignerContext context;
    private final TagConfigManager configManager;
    private final Gson gson = new Gson();
    
    private TagSelectionComponent tagSelectionComponent;
    private JComboBox<String> providerComboBox;
    private JLabel selectionLabel;
    private JLabel matchingConfigsLabel;
    private JTextArea matchingConfigsText;
    private JButton exportButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    private boolean confirmed = false;
    private String selectedPath = "";
    private String selectedProvider = "";
    private List<JsonObject> matchingConfigs = new ArrayList<>();
    
    private static final int RPC_TIMEOUT_SECONDS = 30;

    public SelectiveTagExportDialog(DesignerContext context) {
        super(context.getFrame(), "Selective Tag Export", true);
        this.context = context;
        this.configManager = new TagConfigManager();
        
        initComponents();
        setSize(500, 600);
        setMinimumSize(new Dimension(450, 500));
        DialogUtilities.centerOnOwner(this, context.getFrame());
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(10, 15, 5, 15));
        
        JLabel titleLabel = new JLabel("Select a tag path to export using matching configurations");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        
        JLabel instructionLabel = new JLabel("<html>Choose a specific tag folder to export. The system will find configurations that match your selection and use their settings.</html>");
        instructionLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
        headerPanel.add(instructionLabel, BorderLayout.CENTER);
        
        add(headerPanel, BorderLayout.NORTH);
        
        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(5, 15, 10, 15));
        
        // Provider selection
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        providerPanel.add(new JLabel("Provider:"));
        String[] providers = configManager.getTagProviders();
        providerComboBox = new JComboBox<>(providers);
        providerComboBox.addActionListener(e -> updateTagSelectionForProvider());
        providerPanel.add(providerComboBox);
        mainPanel.add(providerPanel, BorderLayout.NORTH);
        
        // Tag selection tree
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBorder(BorderFactory.createTitledBorder("Select Tag Path"));
        
        String selectedProvider = (String) providerComboBox.getSelectedItem();
        tagSelectionComponent = createTagSelectionComponent(selectedProvider);
        
        JPanel fixedHeightPanel = new JPanel(new BorderLayout());
        fixedHeightPanel.add((Component) tagSelectionComponent, BorderLayout.CENTER);
        fixedHeightPanel.setPreferredSize(new Dimension(400, 200));
        JScrollPane treeScrollPane = new JScrollPane(fixedHeightPanel);
        treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        treePanel.add(treeScrollPane, BorderLayout.CENTER);
        
        selectionLabel = new JLabel("Selection: Provider Root");
        selectionLabel.setFont(selectionLabel.getFont().deriveFont(Font.BOLD));
        selectionLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        treePanel.add(selectionLabel, BorderLayout.SOUTH);
        
        mainPanel.add(treePanel, BorderLayout.CENTER);
        
        // Matching configurations panel
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Matching Configurations"));
        
        matchingConfigsLabel = new JLabel("No matching configurations found");
        matchingConfigsLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        configPanel.add(matchingConfigsLabel, BorderLayout.NORTH);
        
        matchingConfigsText = new JTextArea(4, 40);
        matchingConfigsText.setEditable(false);
        matchingConfigsText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        matchingConfigsText.setBackground(getBackground());
        JScrollPane configScrollPane = new JScrollPane(matchingConfigsText);
        configScrollPane.setPreferredSize(new Dimension(400, 100));
        configPanel.add(configScrollPane, BorderLayout.CENTER);
        
        mainPanel.add(configPanel, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(new EmptyBorder(5, 15, 10, 15));
        
        // Status and progress
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        statusPanel.add(statusLabel, BorderLayout.NORTH);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        buttonPanel.add(statusPanel, BorderLayout.CENTER);
        statusPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        // Buttons using DialogUtilities for consistent styling
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        cancelButton = new JButton(i18n("tagcicd.Dialog.CancelButton"));
        cancelButton.addActionListener(e -> setVisible(false));
        DialogUtilities.styleSecondaryButton(cancelButton);
        
        exportButton = new JButton("Export Selected Path");
        exportButton.addActionListener(this::performExport);
        exportButton.setEnabled(false);
        DialogUtilities.stylePrimaryButton(exportButton);
        
        buttonsPanel.add(cancelButton);
        buttonsPanel.add(exportButton);
        buttonPanel.add(buttonsPanel, BorderLayout.SOUTH);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Initialize matching configs
        updateMatchingConfigurations();
    }

    private TagSelectionComponent createTagSelectionComponent(String provider) {
        TagSelectionComponent component = TagSelectionTreePanel.simpleSingleProvider(context, provider);
        if (component instanceof JComponent) {
            ((JComponent) component).addPropertyChangeListener("selectedNode", evt -> {
                TagPath selectedTagPath = component.getSelectedTagPath();
                updateSelection(selectedTagPath);
            });
        }
        return component;
    }
    
    private void updateTagSelectionForProvider() {
        String selectedProvider = (String) providerComboBox.getSelectedItem();
        
        // Remove old component
        Container parent = ((Component) tagSelectionComponent).getParent();
        if (parent != null) {
            parent.remove((Component) tagSelectionComponent);
        }
        
        // Create new component for selected provider
        tagSelectionComponent = createTagSelectionComponent(selectedProvider);
        
        // Add new component
        if (parent != null) {
            parent.add((Component) tagSelectionComponent);
            parent.revalidate();
            parent.repaint();
        }
        
        // Reset selection
        updateSelection(null);
    }
    
    private void updateSelection(TagPath tagPath) {
        selectedProvider = (String) providerComboBox.getSelectedItem();
        
        if (tagPath == null) {
            selectedPath = "";
            selectionLabel.setText("Selection: Provider Root");
        } else {
            String pathString = tagPath.toStringFull();
            String providerName = tagPath.getSource();
            
            logger.debug("Raw TagPath: toStringFull()='{}', getSource()='{}'", pathString, providerName);
            
            // The issue: tagPath.toStringFull() returns "[provider]path" format for UDT instances
            // We need to extract just the path part after the provider brackets
            if (pathString.startsWith("[" + providerName + "]")) {
                // This handles UDT instances like "[individual]Production" 
                pathString = pathString.substring(("[" + providerName + "]").length());
            } else if (pathString.startsWith(providerName + "/")) {
                // This handles regular folder paths like "individual/Production"
                pathString = pathString.substring(providerName.length() + 1);
            } else if (pathString.equals(providerName)) {
                // This handles provider root selection
                pathString = "";
            }
            
            selectedPath = pathString;
            selectionLabel.setText("Selection: " + (selectedPath.isEmpty() ? "Provider Root" : selectedPath));
            
            logger.debug("Extracted selectedPath: '{}'", selectedPath);
        }
        
        updateMatchingConfigurations();
    }
    
    private void updateMatchingConfigurations() {
        matchingConfigs.clear();
        
        try {
            TagCICDRPC rpc = ModuleRPCFactory.create(TagCICDConstants.MODULE_ID, TagCICDRPC.class);
            String configJson = rpc.getTagConfig();
            JsonArray configs = gson.fromJson(configJson, JsonArray.class);
            
            // Find configurations that match the selected path
            for (JsonElement element : configs) {
                JsonObject config = element.getAsJsonObject();
                String configProvider = config.get("provider").getAsString();
                String configBaseTagPath = config.get("baseTagPath").getAsString();
                
                // Check if provider matches
                if (!configProvider.equals(selectedProvider)) {
                    continue;
                }
                
                // Check if selected path is under the configuration's base path
                if (isPathUnderBase(selectedPath, configBaseTagPath)) {
                    matchingConfigs.add(config);
                }
            }
            
            updateMatchingConfigsDisplay();
            
        } catch (Exception e) {
            logger.error("Failed to load configurations", e);
            matchingConfigsLabel.setText("Error loading configurations");
            matchingConfigsText.setText("Failed to load configurations: " + e.getMessage());
        }
    }
    
    private boolean isPathUnderBase(String selectedPath, String basePath) {
        // If base path is empty (provider root), it matches everything
        if (basePath == null || basePath.isEmpty()) {
            return true;
        }
        
        // If selected path is empty (provider root), only match empty base paths
        if (selectedPath == null || selectedPath.isEmpty()) {
            return basePath.isEmpty();
        }
        
        // Check if selected path starts with base path
        return selectedPath.startsWith(basePath) && 
               (selectedPath.length() == basePath.length() || selectedPath.charAt(basePath.length()) == '/');
    }
    
    private void updateMatchingConfigsDisplay() {
        if (matchingConfigs.isEmpty()) {
            matchingConfigsLabel.setText("No matching configurations found");
            matchingConfigsText.setText("No configurations match the selected path.\nPlease create a configuration that covers this path first.");
            exportButton.setEnabled(false);
        } else {
            matchingConfigsLabel.setText(matchingConfigs.size() + " matching configuration(s) found:");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < matchingConfigs.size(); i++) {
                JsonObject config = matchingConfigs.get(i);
                sb.append(String.format("%d. %s -> %s [%s]\n", 
                    i + 1,
                    config.get("baseTagPath").getAsString().isEmpty() ? "[Provider Root]" : config.get("baseTagPath").getAsString(),
                    config.get("sourcePath").getAsString(),
                    config.get("exportMode").getAsString()));
            }
            matchingConfigsText.setText(sb.toString());
            exportButton.setEnabled(true);
        }
    }
    
    private void performExport(ActionEvent e) {
        if (matchingConfigs.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No matching configurations found for the selected path.", 
                "No Configurations", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Disable controls
        exportButton.setEnabled(false);
        cancelButton.setEnabled(false);
        cancelButton.setText("Close");
        providerComboBox.setEnabled(false);
        ((Component) tagSelectionComponent).setEnabled(false);
        
        // Show progress
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setMinimum(0);
        progressBar.setMaximum(matchingConfigs.size());
        progressBar.setValue(0);
        statusLabel.setText("Exporting selected path using matching configurations...");
        
        // Perform export in background
        SwingWorker<Void, String> exportWorker = new SwingWorker<Void, String>() {
            private JsonObject exportResults = new JsonObject();
            
            @Override
            protected Void doInBackground() {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                try {
                    TagCICDRPC rpc = ModuleRPCFactory.create(TagCICDConstants.MODULE_ID, TagCICDRPC.class);
                    
                    int completed = 0;
                    for (JsonObject config : matchingConfigs) {
                        String configBaseTagPath = config.get("baseTagPath").getAsString();
                        String sourcePath = config.get("sourcePath").getAsString();
                        String exportMode = config.get("exportMode").getAsString();
                        boolean excludeUdtDefinitions = config.has("excludeUdtDefinitions") 
                            ? config.get("excludeUdtDefinitions").getAsBoolean() 
                            : false;
                        
                        // Calculate the target file path based on export mode
                        final String targetFilePath;
                        if ("individualFiles".equals(exportMode)) {
                            // For individual files mode, append selected path to create subdirectory
                            if (!selectedPath.isEmpty()) {
                                String tempPath = sourcePath;
                                if (!sourcePath.endsWith("/") && !sourcePath.endsWith("\\")) {
                                    tempPath += "/";
                                }

                                // Remove the baseTagPath from selectedPath since sourcePath already contains it
                                String adjustedSelectedPath = selectedPath;
                                if (selectedPath.startsWith(configBaseTagPath)) {
                                    if (selectedPath.equals(configBaseTagPath)) {
                                        // Selected path is exactly the base path
                                        adjustedSelectedPath = "";
                                    } else if (selectedPath.startsWith(configBaseTagPath + "/")) {
                                        // Selected path is deeper than base path
                                        adjustedSelectedPath = selectedPath.substring(configBaseTagPath.length() + 1);
                                    }
                                }

                                if (!adjustedSelectedPath.isEmpty()) {
                                    targetFilePath = tempPath + adjustedSelectedPath;
                                } else {
                                    targetFilePath = sourcePath;
                                }
                            } else {
                                targetFilePath = sourcePath;
                            }
                        } else if ("structuredByType".equals(exportMode)) {
                            // For structured mode, apply the same path adjustment logic as individual files
                            // Remove the baseTagPath from selectedPath since sourcePath already contains it
                            String adjustedSelectedPath = selectedPath;
                            if (selectedPath.startsWith(configBaseTagPath)) {
                                if (selectedPath.equals(configBaseTagPath)) {
                                    // Selected path is exactly the base path
                                    adjustedSelectedPath = "";
                                } else if (selectedPath.startsWith(configBaseTagPath + "/")) {
                                    // Selected path is deeper than base path
                                    adjustedSelectedPath = selectedPath.substring(configBaseTagPath.length() + 1);
                                }
                            }

                            if (!adjustedSelectedPath.isEmpty()) {
                                String tempPath = sourcePath;
                                if (!sourcePath.endsWith("/") && !sourcePath.endsWith("\\")) {
                                    tempPath += "/";
                                }
                                targetFilePath = tempPath + adjustedSelectedPath;
                            } else {
                                targetFilePath = sourcePath;
                            }
                        } else {
                            // For single file mode, use the exact file path from config
                            // The selective update logic will handle merging with existing content
                            targetFilePath = sourcePath;
                        }
                        
                        // Use the selected path as the actual export path from the tag provider
                        final String actualExportPath = selectedPath;
                        
                        publish("Exporting " + actualExportPath + " to: " + targetFilePath);
                        logger.info("Selective export: provider={}, tagPath={}, targetFilePath={}, exportMode={}", 
                                   selectedProvider, actualExportPath, targetFilePath, exportMode);
                        
                        try {
                            Future<String> future;
                            
                            // Handle different export modes for selective export
                            if ("singleFile".equals(exportMode) || "structuredByType".equals(exportMode)) {
                                // For single file and structured modes, use the new selective export RPC method
                                future = executor.submit(() -> 
                                    rpc.performSelectiveExport(selectedProvider, actualExportPath, targetFilePath, 
                                                             exportMode, excludeUdtDefinitions, configBaseTagPath));
                            } else {
                                // For individual files mode, use the regular export (already works correctly)
                                future = executor.submit(() -> 
                                    rpc.exportTags(selectedProvider, actualExportPath, targetFilePath, true, false, 
                                                 exportMode, true, excludeUdtDefinitions));
                            }
                            
                            String result = future.get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            JsonObject exportResult = gson.fromJson(result, JsonObject.class);
                            
                            exportResults.addProperty(targetFilePath,
                                exportResult.get("success").getAsBoolean()
                                    ? "Exported successfully"
                                    : "Failed: " + exportResult.get("error").getAsString());
                                    
                        } catch (Exception ex) {
                            logger.error("Export failed for target: " + targetFilePath, ex);
                            exportResults.addProperty(targetFilePath, "Failed: " + ex.getMessage());
                        }
                        
                        // Update progress
                        completed++;
                        setProgress((completed * 100) / matchingConfigs.size());
                    }
                    
                } catch (Exception ex) {
                    logger.error("Export operation failed", ex);
                    for (JsonObject config : matchingConfigs) {
                        String sourcePath = config.get("sourcePath").getAsString();
                        String exportMode = config.get("exportMode").getAsString();
                        String targetFilePath;
                        
                        if ("individualFiles".equals(exportMode)) {
                            // For individual files mode, append selected path
                            targetFilePath = sourcePath;
                            if (!selectedPath.isEmpty()) {
                                if (!sourcePath.endsWith("/") && !sourcePath.endsWith("\\")) {
                                    targetFilePath += "/";
                                }
                                targetFilePath += selectedPath;
                            }
                        } else {
                            // For single file and structured modes, use exact file path
                            targetFilePath = sourcePath;
                        }
                        
                        exportResults.addProperty(targetFilePath, "Failed: " + ex.getMessage());
                    }
                } finally {
                    executor.shutdown();
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    
                    // Show results
                    StringBuilder resultMessage = new StringBuilder("Export completed:\n");
                    for (String sourcePath : exportResults.keySet()) {
                        resultMessage.append("• ").append(sourcePath).append(": ")
                                   .append(exportResults.get(sourcePath).getAsString()).append("\n");
                    }
                    
                    JOptionPane.showMessageDialog(SelectiveTagExportDialog.this, 
                        resultMessage.toString(), 
                        "Export Results", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    confirmed = true;
                    setVisible(false);
                    
                } catch (Exception ex) {
                    logger.error("Export operation error", ex);
                    JOptionPane.showMessageDialog(SelectiveTagExportDialog.this, 
                        "Export error: " + ex.getMessage(),
                        "Export Error", 
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setVisible(false);
                    statusLabel.setText(" ");
                }
            }
        };
        
        // Add progress listener
        exportWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        exportWorker.execute();
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Performs a selective update for single file and structured export modes.
     * Loads the existing file, exports just the selected portion, and merges the results.
     */
    private String performSelectiveUpdate(TagCICDRPC rpc, String provider, String tagPath, 
                                        String targetFilePath, String exportMode, boolean excludeUdtDefinitions) {
        try {
            // Step 1: Get the JSON data for the selected portion using the JSON export method
            String newDataJson = rpc.exportTagsToJson(provider, tagPath, true, false);
            JsonObject newDataResult = gson.fromJson(newDataJson, JsonObject.class);
            
            // Check if this is an error response (has "success" field)
            if (newDataResult.has("success") && !newDataResult.get("success").getAsBoolean()) {
                return newDataJson; // Return the error as-is
            }
            
            // The response is the actual tag data, not wrapped in a "data" field
            JsonObject newData = newDataResult;
            
            // Step 2: Load existing file if it exists
            // Need to resolve relative paths against the gateway's data directory
            String absoluteFilePath;
            if (new File(targetFilePath).isAbsolute()) {
                absoluteFilePath = targetFilePath;
            } else {
                // Get gateway install directory and resolve relative path
                String gatewayDir = rpc.getInstallDirectory();
                absoluteFilePath = Paths.get(gatewayDir, targetFilePath).toString();
            }
            
            File targetFile = new File(absoluteFilePath);
            JsonObject existingData;
            
            if (targetFile.exists()) {
                try {
                    String existingContent = new String(Files.readAllBytes(Paths.get(absoluteFilePath)));
                    existingData = gson.fromJson(existingContent, JsonObject.class);
                } catch (Exception e) {
                    logger.warn("Could not read existing file {}, creating new: {}", absoluteFilePath, e.getMessage());
                    existingData = new JsonObject();
                }
            } else {
                existingData = new JsonObject();
                // Ensure parent directories exist
                targetFile.getParentFile().mkdirs();
            }
            
            // Step 3: Merge the new data into the existing data
            JsonObject mergedData = mergeSelectiveData(existingData, newData, tagPath, exportMode);
            
            // Step 4: Write the merged data back to the file
            try (FileWriter writer = new FileWriter(targetFile)) {
                writer.write(gson.toJson(mergedData));
            }
            
            // Step 5: Return success result
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Selective update completed successfully");
            return gson.toJson(result);
            
        } catch (Exception e) {
            logger.error("Selective update failed", e);
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("error", "Selective update failed: " + e.getMessage());
            return gson.toJson(result);
        }
    }
    
    /**
     * Merges new tag data into existing data for selective updates.
     */
    private JsonObject mergeSelectiveData(JsonObject existingData, JsonObject newData, String selectedPath, String exportMode) {
        if ("singleFile".equals(exportMode)) {
            return mergeSingleFileData(existingData, newData, selectedPath);
        } else if ("structuredByType".equals(exportMode)) {
            return mergeStructuredData(existingData, newData, selectedPath);
        }
        return newData; // Fallback
    }
    
    /**
     * Merge data for single file export mode.
     */
    private JsonObject mergeSingleFileData(JsonObject existingData, JsonObject newData, String selectedPath) {
        // For single file mode, we need to replace the specific path section
        if (!existingData.has("tags")) {
            existingData.add("tags", new JsonArray());
        }
        
        JsonArray existingTags = existingData.getAsJsonArray("tags");
        JsonArray newTags = newData.has("tags") ? newData.getAsJsonArray("tags") : new JsonArray();
        
        // Remove existing tags that match the selected path
        JsonArray filteredTags = new JsonArray();
        for (JsonElement element : existingTags) {
            JsonObject tag = element.getAsJsonObject();
            String tagPath = tag.has("path") ? tag.get("path").getAsString() : "";
            
            // Keep tags that don't start with the selected path
            if (selectedPath.isEmpty() || !tagPath.startsWith(selectedPath)) {
                filteredTags.add(tag);
            }
        }
        
        // Add all new tags
        for (JsonElement element : newTags) {
            filteredTags.add(element);
        }
        
        // Create result
        JsonObject result = new JsonObject();
        result.add("tags", filteredTags);
        
        // Copy other properties from existing data
        for (String key : existingData.keySet()) {
            if (!"tags".equals(key)) {
                result.add(key, existingData.get(key));
            }
        }
        
        return result;
    }
    
    /**
     * Merge data for structured files export mode.
     */
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
            
            // For UDTs, we typically want to replace entirely since they're type definitions
            // But we should still filter by path if applicable
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
}