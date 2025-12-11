package dev.bwdesigngroup.ignition.tag_cicd.designer.dialog;

import dev.bwdesigngroup.ignition.tag_cicd.common.TagCICDRPC;
import dev.bwdesigngroup.ignition.tag_cicd.common.constants.TagCICDConstants;
import dev.bwdesigngroup.ignition.tag_cicd.common.model.ExportMode;
import dev.bwdesigngroup.ignition.tag_cicd.designer.model.TagConfigManager;
import dev.bwdesigngroup.ignition.tag_cicd.designer.util.DialogUtilities;
import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.BasicTagPath;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.tags.tree.selection.TagSelectionComponent;
import com.inductiveautomation.ignition.designer.tags.tree.selection.TagSelectionTreePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog for editing a tag configuration with enhanced validation.
 */
public class TagConfigEditorDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(TagConfigEditorDialog.class.getName());
    private static String INSTALL_DIR_PREFIX;

    private final DesignerContext context;

    private final JComboBox<String> providerComboBox;
    private TagSelectionComponent tagSelectionComponent;
    private JCheckBox exportProviderRootCheckBox;
    private final JTextField exportPathField;
    private final JLabel fullPathPreviewLabel;
    private final JComboBox<String> exportModeComboBox;
    private final JComboBox<String> collisionPolicyComboBox;
    private final JCheckBox includeUdtDefinitionsCheckBox;
    private final JLabel selectionStatusLabel;
    private final JLabel validationWarningLabel;

    private boolean confirmed = false;
    private JsonObject configObject;
    private boolean isRootSelection = true;
    private final TagConfigManager configManager;
    private String pendingTagPath = null;

    public TagConfigEditorDialog(DesignerContext context, JsonObject config, TagConfigManager configManager) {
        super(context.getFrame(), config == null ? "Add Configuration" : "Edit Configuration", true);
        this.context = context;
        this.configManager = configManager;

        try {
            TagCICDRPC rpc = ModuleRPCFactory.create(TagCICDConstants.MODULE_ID, TagCICDRPC.class);
            INSTALL_DIR_PREFIX = rpc.getInstallDirectory() + File.separator;
        } catch (Exception e) {
            logger.warn("Could not fetch install directory, using placeholder", e);
            INSTALL_DIR_PREFIX = "<INSTALL_DIRECTORY>" + File.separator;
        }

        // Create components
        JLabel providerLabel = new JLabel("Provider:");
        String[] providers = configManager.getTagProviders();
        providerComboBox = new JComboBox<>(providers);

        selectionStatusLabel = new JLabel("Selection: Provider Root");
        selectionStatusLabel.setFont(selectionStatusLabel.getFont().deriveFont(Font.BOLD, 12f));

        // Add validation warning label
        validationWarningLabel = new JLabel();
        validationWarningLabel.setForeground(Color.RED);
        validationWarningLabel.setFont(validationWarningLabel.getFont().deriveFont(Font.BOLD, 11f));
        validationWarningLabel.setVisible(false);

        exportProviderRootCheckBox = new JCheckBox("Export Provider Root", true);
        exportProviderRootCheckBox.addActionListener(e -> {
            if (exportProviderRootCheckBox.isSelected()) {
                tagSelectionComponent.reset();
                isRootSelection = true;
                selectionStatusLabel.setText("Selection: Provider Root");
                updateUdtCheckboxState();
                validateSelection();
            }
        });
        
        JLabel exportPathLabel = new JLabel("Export Path:");
        exportPathField = new JTextField(20);
        fullPathPreviewLabel = new JLabel(INSTALL_DIR_PREFIX);
        fullPathPreviewLabel.setForeground(Color.GRAY);

        JLabel exportModeLabel = new JLabel("Export Mode:");
        String[] exportModes = Arrays.stream(ExportMode.values())
                .map(ExportMode::getDisplayName)
                .toArray(String[]::new);
        exportModeComboBox = new JComboBox<>(exportModes);

        JLabel collisionPolicyLabel = new JLabel("Collision Policy:");
        String[] collisionPolicies = {
                "Abort (a)", "Merge (m)", "Overwrite (o)", "Delete & Replace (d)"
        };
        collisionPolicyComboBox = new JComboBox<>(collisionPolicies);

        includeUdtDefinitionsCheckBox = new JCheckBox("Include UDT Definitions");

        // Populate fields if editing
        if (config != null) {
            String provider = config.get("provider").getAsString();
            for (int i = 0; i < providers.length; i++) {
                if (providers[i].equals(provider)) {
                    providerComboBox.setSelectedIndex(i);
                    break;
                }
            }
            exportPathField.setText(config.get("sourcePath").getAsString());
            String exportMode = config.get("exportMode").getAsString();
            for (int i = 0; i < ExportMode.values().length; i++) {
                if (ExportMode.values()[i].getCode().equals(exportMode)) {
                    exportModeComboBox.setSelectedIndex(i);
                    break;
                }
            }
            String collisionPolicy = config.get("collisionPolicy").getAsString();
            int collisionIndex = switch (collisionPolicy) {
                case "a" -> 0;
                case "m" -> 1;
                case "o" -> 2;
                case "d" -> 3;
                default -> 0;
            };
            collisionPolicyComboBox.setSelectedIndex(collisionIndex);
            boolean includeUdtDefinitions = !config.has("excludeUdtDefinitions") ||
                    !config.get("excludeUdtDefinitions").getAsBoolean();
            includeUdtDefinitionsCheckBox.setSelected(includeUdtDefinitions);
        } else {
            providerComboBox.setSelectedIndex(0);
            exportModeComboBox.setSelectedItem(ExportMode.INDIVIDUAL_FILES.getDisplayName());
            collisionPolicyComboBox.setSelectedIndex(2); // Overwrite
            includeUdtDefinitionsCheckBox.setSelected(true);
        }

        String selectedProvider = (String) providerComboBox.getSelectedItem();
        tagSelectionComponent = createTagSelectionComponent(selectedProvider);

        // Initialize tag selection from config if editing
        if (config != null && config.has("baseTagPath")) {
            String baseTagPath = config.get("baseTagPath").getAsString();
            if (!baseTagPath.isEmpty()) {
                isRootSelection = false;
                selectionStatusLabel.setText("Selection: " + baseTagPath);
                exportProviderRootCheckBox.setSelected(false);
                setTagPathOnComponent(selectedProvider, baseTagPath);
                validateSelection(); // Validate on load
            }
        }

        updateUdtCheckboxState();

        providerComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateTagSelectionComponentForProvider((String) e.getItem());
                isRootSelection = true;
                selectionStatusLabel.setText("Selection: Provider Root");
                updateUdtCheckboxState();
                validateSelection();
            }
        });

        // Add listener to update full path preview
        exportPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFullPathPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateFullPathPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateFullPathPreview();
            }
        });
        

        // Create the content panel
        JPanel contentPanel = new JPanel(new BorderLayout());

        // Create the form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(10, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Provider row
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(providerLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        formPanel.add(providerComboBox, gbc);

        // Tag Browser section with header
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        formPanel.add(selectionStatusLabel, gbc);

        // Validation warning label
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        formPanel.add(validationWarningLabel, gbc);

        // Tag Browser
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        JPanel tagTreePanel = new JPanel(new BorderLayout());
        tagTreePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        JPanel fixedHeightPanel = new JPanel(new BorderLayout());
        fixedHeightPanel.add((Component) tagSelectionComponent, BorderLayout.CENTER);
        fixedHeightPanel.setPreferredSize(new Dimension(400, 160));
        JScrollPane scrollPane = new JScrollPane(fixedHeightPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        tagTreePanel.add(scrollPane, BorderLayout.CENTER);
        formPanel.add(tagTreePanel, gbc);

        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Export Mode row
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        formPanel.add(exportModeLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        formPanel.add(exportModeComboBox, gbc);

        // Collision Policy row
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        formPanel.add(collisionPolicyLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        formPanel.add(collisionPolicyComboBox, gbc);

        // Export Path row with prefix and preview
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        formPanel.add(exportPathLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        formPanel.add(exportPathField, gbc);

        // Full path preview row
        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        formPanel.add(fullPathPreviewLabel, gbc);

        // Export Provider Root checkbox row
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        formPanel.add(exportProviderRootCheckBox, gbc);

        // Include UDT Definitions checkbox row
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        formPanel.add(includeUdtDefinitionsCheckBox, gbc);

        contentPanel.add(formPanel, BorderLayout.CENTER);

        // Create button panel using DialogUtilities for consistent styling
        JPanel buttonPanel = DialogUtilities.createButtonPanel(
            this::saveConfiguration,
            this::dispose,
            config == null ? "Add" : "Save",
            "Cancel"
        );
        buttonPanel.setBorder(new EmptyBorder(0, 15, 10, 15));

        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);
        pack();
        setSize(550, 600); // Increased height for validation warning
        setResizable(true);
        DialogUtilities.centerOnOwner(this, context.getFrame());

        // Update preview initially
        updateFullPathPreview();
        validateSelection();
    }

    private void updateFullPathPreview() {
        String userInput = exportPathField.getText().trim();
        fullPathPreviewLabel.setText(INSTALL_DIR_PREFIX + userInput);
    }

    private TagSelectionComponent createTagSelectionComponent(String provider) {
        TagSelectionComponent component = TagSelectionTreePanel.simpleSingleProvider(context, provider);
        if (component instanceof JComponent) {
            ((JComponent) component).addPropertyChangeListener("selectedNode", evt -> {
                TagPath selectedPath = component.getSelectedTagPath();
                if (selectedPath == null) {
                    isRootSelection = true;
                    selectionStatusLabel.setText("Selection: Provider Root");
                    exportProviderRootCheckBox.setSelected(true);
                } else {
                    String pathString = selectedPath.toStringFull();
                    String providerName = selectedPath.getSource();
                    if (pathString.startsWith(providerName + "/")) {
                        pathString = pathString.substring(providerName.length() + 1);
                    }
                    isRootSelection = pathString.isEmpty();
                    if (isRootSelection) {
                        selectionStatusLabel.setText("Selection: Provider Root");
                        exportProviderRootCheckBox.setSelected(true);
                    } else {
                        selectionStatusLabel.setText("Selection: " + pathString);
                        exportProviderRootCheckBox.setSelected(false);
                    }
                }
                updateUdtCheckboxState();
                validateSelection();
            });
            
            // Add listener to handle pasted full paths with provider
            addComboBoxListener((JComponent) component);
        }
        return component;
    }

    /**
     * Recursively finds and adds listeners to JComboBox components within the tag selection component
     * to handle pasted full tag paths with provider information.
     */
    private void addComboBoxListener(JComponent parent) {
        // Recursively search for JComboBox components
        findAndListenToComboBoxes(parent);
    }
    
    @SuppressWarnings("unchecked")
    private void findAndListenToComboBoxes(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox) {
                JComboBox<Object> comboBox = (JComboBox<Object>) component;
                
                // Add action listener to detect when user types/pastes full paths
                comboBox.addActionListener(e -> {
                    handleComboBoxInput(comboBox);
                });
                
                // Also add a focus listener as backup
                comboBox.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        handleComboBoxInput(comboBox);
                    }
                });
                
            } else if (component instanceof Container) {
                findAndListenToComboBoxes((Container) component);
            }
        }
    }
    
    private void handleComboBoxInput(JComboBox<Object> comboBox) {
        try {
            Object selectedItem = comboBox.getSelectedItem();
            if (selectedItem != null) {
                String input = selectedItem.toString().trim();
                
                // Check if this looks like a full tag path with provider (e.g., "[individual]Production/Line1Motor")
                if (input.startsWith("[") && input.contains("]")) {
                    SwingUtilities.invokeLater(() -> {
                        // Use our existing setTagPathOnComponent method which handles cross-provider paths
                        setTagPathOnComponent((String) providerComboBox.getSelectedItem(), input);
                    });
                }
            }
        } catch (Exception ex) {
            logger.warn("Error handling combo box input", ex);
        }
    }

    /**
     * Validates the current tag selection and shows helpful info for UDT exports.
     */
    private void validateSelection() {
        String infoMessage = null;

        if (!isRootSelection) {
            TagPath selectedPath = tagSelectionComponent.getSelectedTagPath();
            if (selectedPath != null) {
                String pathString = selectedPath.toStringFull();
                String providerName = selectedPath.getSource();
                if (pathString.startsWith(providerName + "/")) {
                    pathString = pathString.substring(providerName.length() + 1);
                }

                // Provide helpful info for _types_ folder selections
                if (pathString.startsWith("_types_/")) {
                    infoMessage = "ℹ️ Exporting UDT definitions from: " + pathString
                            + ". Make sure this folder contains the UDT definitions you want to export.";
                    validationWarningLabel.setForeground(new Color(0, 100, 200)); // Blue for info
                } else if (pathString.equals("_types_")) {
                    infoMessage = "ℹ️ Exporting all UDT definitions from the _types_ folder.";
                    validationWarningLabel.setForeground(new Color(0, 100, 200)); // Blue for info
                }
            }
        }

        if (infoMessage != null) {
            validationWarningLabel.setText("<html><div style='width: 400px;'>" + infoMessage + "</div></html>");
            validationWarningLabel.setVisible(true);
        } else {
            validationWarningLabel.setVisible(false);
        }

        // Trigger layout update
        revalidate();
        repaint();
    }

    private void updateUdtCheckboxState() {
        includeUdtDefinitionsCheckBox.setEnabled(isRootSelection);
        if (!isRootSelection) {
            includeUdtDefinitionsCheckBox.setSelected(false);
            includeUdtDefinitionsCheckBox
                    .setToolTipText("UDT Definitions can only be included when exporting from the provider root");
        } else {
            includeUdtDefinitionsCheckBox.setToolTipText(null);
        }
    }

    private void setTagPathOnComponent(String provider, String pathString) {
        try {
            // Handle fully qualified paths that already include provider (e.g., "[default]Production")
            String actualProvider = provider;
            String actualPath = pathString;
            
            // Check if the path starts with a provider in brackets
            if (pathString.startsWith("[") && pathString.contains("]")) {
                int closeBracketIndex = pathString.indexOf("]");
                String pathProvider = pathString.substring(1, closeBracketIndex);
                String pathAfterProvider = pathString.substring(closeBracketIndex + 1);
                
                // If the provider in the path matches our current provider, use the path without provider
                if (pathProvider.equals(provider)) {
                    actualPath = pathAfterProvider;
                } else {
                    // Path is for a different provider, update our provider selection
                    actualProvider = pathProvider;
                    actualPath = pathAfterProvider;
                    
                    // Store the path to set after provider change
                    pendingTagPath = pathAfterProvider;
                    
                    // Update provider combo box if needed
                    for (int i = 0; i < providerComboBox.getItemCount(); i++) {
                        if (providerComboBox.getItemAt(i).equals(pathProvider)) {
                            providerComboBox.setSelectedIndex(i);
                            break;
                        }
                    }
                    return; // Let the provider change handler create new component and set path
                }
            }
            
            List<String> pathComponents = new ArrayList<>();
            if (!actualPath.isEmpty()) {
                String[] parts = actualPath.split("/");
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        pathComponents.add(part);
                    }
                }
            }
            TagPath tagPath = new BasicTagPath(actualProvider, pathComponents);
            tagSelectionComponent.setSelectedTagPath(tagPath);
        } catch (Exception e) {
            logger.warn("Unable to set tag path: " + pathString, e);
        }
    }
    

    private void updateTagSelectionComponentForProvider(String provider) {
        // Store current selection state to potentially restore it
        String currentBaseTagPath = "";
        boolean wasRootSelection = isRootSelection;
        
        if (!isRootSelection && tagSelectionComponent != null) {
            TagPath selectedPath = tagSelectionComponent.getSelectedTagPath();
            if (selectedPath != null) {
                String fullPathString = selectedPath.toStringFull();
                String currentProviderName = selectedPath.getSource();
                if (fullPathString.startsWith(currentProviderName + "/")) {
                    currentBaseTagPath = fullPathString.substring(currentProviderName.length() + 1);
                }
            }
        }
        
        Container parent = ((Component) tagSelectionComponent).getParent();
        if (parent != null) {
            parent.remove((Component) tagSelectionComponent);
        }
        tagSelectionComponent = createTagSelectionComponent(provider);
        if (parent != null) {
            parent.add((Component) tagSelectionComponent);
            parent.revalidate();
            parent.repaint();
        }
        
        // Check if we have a pending tag path to set (from cross-provider path setting)
        String pathToSet = pendingTagPath;
        if (pathToSet != null) {
            pendingTagPath = null; // Clear the pending path
        } else if (!wasRootSelection && !currentBaseTagPath.isEmpty()) {
            pathToSet = currentBaseTagPath;
        }
        
        // Try to restore/set the selection 
        if (pathToSet != null && !pathToSet.isEmpty()) {
            // Attempt to set the path on the new provider
            setTagPathOnComponent(provider, pathToSet);
            // Check if the path was successfully set
            TagPath restoredPath = tagSelectionComponent.getSelectedTagPath();
            if (restoredPath != null) {
                String pathString = restoredPath.toStringFull();
                String providerName = restoredPath.getSource();
                if (pathString.startsWith(providerName + "/")) {
                    pathString = pathString.substring(providerName.length() + 1);
                }
                if (!pathString.isEmpty()) {
                    isRootSelection = false;
                    selectionStatusLabel.setText("Selection: " + pathString);
                    exportProviderRootCheckBox.setSelected(false);
                    updateUdtCheckboxState();
                    validateSelection();
                    return;
                }
            }
        }
        
        // Default to root selection if restoration failed or was root
        isRootSelection = true;
        selectionStatusLabel.setText("Selection: Provider Root");
        exportProviderRootCheckBox.setSelected(true);
        updateUdtCheckboxState();
        validateSelection();
    }

    private void saveConfiguration() {
        if (validateInputs()) {
            String exportPath = exportPathField.getText().trim();

            // Check for overlapping paths
            boolean hasOverlap = false;
            if (configObject != null) {
                // Editing existing config
                int index = -1;
                for (int i = 0; i < configManager.getConfigArray().size(); i++) {
                    JsonElement existingConfig = configManager.getConfigArray().get(i);
                    if (existingConfig.equals(configObject)) {
                        index = i;
                        break;
                    }
                }
                hasOverlap = configManager.hasOverlappingPath(index, exportPath);
            } else {
                // New config
                hasOverlap = configManager.hasOverlappingPath(-1, exportPath);
            }

            if (hasOverlap) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Warning: This export path overlaps with another configuration.\n" +
                                "This may cause issues during import. Continue anyway?",
                        "Path Overlap Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            confirmed = true;
            configObject = new JsonObject();
            String providerName = (String) providerComboBox.getSelectedItem();
            configObject.addProperty("provider", providerName);

            TagPath selectedPath = tagSelectionComponent.getSelectedTagPath();
            String baseTagPath = "";
            if (selectedPath != null && !isRootSelection) {
                String fullPathString = selectedPath.toStringFull();
                providerName = selectedPath.getSource();
                if (fullPathString.startsWith(providerName + "/")) {
                    baseTagPath = fullPathString.substring(providerName.length() + 1);
                } else {
                    baseTagPath = fullPathString;
                }
                baseTagPath = baseTagPath.replace("[" + providerName + "]", "");
            }
            configObject.addProperty("baseTagPath", baseTagPath);
            configObject.addProperty("sourcePath", exportPathField.getText().trim());

            String exportModeDisplayName = (String) exportModeComboBox.getSelectedItem();
            for (ExportMode mode : ExportMode.values()) {
                if (mode.getDisplayName().equals(exportModeDisplayName)) {
                    configObject.addProperty("exportMode", mode.getCode());
                    break;
                }
            }

            int collisionIndex = collisionPolicyComboBox.getSelectedIndex();
            String collisionPolicy = switch (collisionIndex) {
                case 0 -> "a"; // Abort
                case 1 -> "m"; // Merge
                case 2 -> "o"; // Overwrite
                case 3 -> "d"; // Delete and Replace
                default -> "a";
            };
            configObject.addProperty("collisionPolicy", collisionPolicy);

            if (isRootSelection && !includeUdtDefinitionsCheckBox.isSelected()) {
                configObject.addProperty("excludeUdtDefinitions", true);
            }

            dispose();
        }
    }

    private boolean validateInputs() {
        if (providerComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Please select a tag provider", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (exportPathField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify an export path", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public JsonObject getConfigObject() {
        return configObject;
    }
}