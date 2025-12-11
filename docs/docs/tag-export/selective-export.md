---
id: selective-export
title: Selective Tag Export
sidebar_label: Selective Export
---

# Selective Tag Export

The Selective Export feature provides a streamlined way to export specific tag paths without manually configuring new export settings. Instead of creating new configurations or selecting from a list, you simply choose a tag path and the system automatically finds and uses matching configurations.

## Overview

Selective Export simplifies the export process by:

- **Automatic Configuration Matching**: Finds configurations that apply to your selected tag path
- **Zero Configuration**: No need to create new settings for one-time exports
- **Intelligent Path Matching**: Uses existing configuration rules and settings
- **Quick Access**: Available directly from the main toolbar

## How It Works

### 1. Configuration Matching Logic

When you select a tag path, the system searches through your existing configurations to find matches based on:

- **Provider Match**: Configuration must use the same tag provider
- **Path Hierarchy**: Configuration's `baseTagPath` must be a parent of or match your selected path

### 2. Matching Examples

Given these configurations:
```json
[
  {
    "provider": "default",
    "baseTagPath": "Production",
    "sourcePath": "data/tags/production"
  },
  {
    "provider": "default", 
    "baseTagPath": "Production/Line1",
    "sourcePath": "data/tags/line1"
  },
  {
    "provider": "individual",
    "baseTagPath": "Sensors",
    "sourcePath": "data/tags/sensors"
  }
]
```

**Selected Path**: `[default]Production/Line1/Motors`

**Matching Configurations**:
- ✅ Configuration 1: `Production` contains `Production/Line1/Motors`
- ✅ Configuration 2: `Production/Line1` contains `Production/Line1/Motors`  
- ❌ Configuration 3: Different provider (`individual` vs `default`)

## Using Selective Export

### Step-by-Step Process

1. **Open Selective Export**
   - Click the 🔍 **Selective Export** button in the Tag CICD toolbar
   - Or select it from the Tools menu

2. **Select Provider**
   - Choose the tag provider from the dropdown
   - This filters the available tag paths

3. **Navigate and Select**
   - Use the tag tree to browse your tag structure
   - Click on any tag or folder to select it
   - The selection display shows your current choice

4. **Review Matching Configurations**
   - The bottom panel automatically shows configurations that match your selection
   - Each matching configuration displays its export settings and destination

5. **Execute Export**
   - Click **"Export Selected Path"** to run all matching configurations
   - The system exports using each configuration's individual settings

### Interactive Features

- **Real-time Updates**: Matching configurations update immediately as you change your selection
- **Path Display**: The selection label clearly shows your current choice
- **Configuration Preview**: See which configurations will be used before exporting

## Advanced Usage

### Path Selection Strategies

**Folder Selection**: Select a parent folder to export all contained tags
```
Selected: Production/Line1
Exports: All tags under Line1 using matching configurations
```

**Specific Tag Selection**: Select an individual tag for targeted export
```
Selected: Production/Line1/Motor1/Speed
Exports: Just the Speed tag (if configurations support single tag export)
```

**Root Level Selection**: Select at the provider root to use all configurations
```
Selected: [default] (Provider Root)
Exports: Uses all configurations for the default provider
```

### Multiple Configuration Benefits

When multiple configurations match your selection:

- **Different Export Modes**: Each configuration can use its preferred export format
- **Multiple Destinations**: Export the same tags to different locations
- **Varied Settings**: Each export uses its own collision policy and options

## Best Practices

### Configuration Design

**Hierarchical Structure**: Design configurations with clear hierarchies
```json
[
  {
    "baseTagPath": "",
    "sourcePath": "data/tags/full-backup"
  },
  {
    "baseTagPath": "Production", 
    "sourcePath": "data/tags/production"
  },
  {
    "baseTagPath": "Production/Line1",
    "sourcePath": "data/tags/line1"  
  }
]
```

**Specific Purposes**: Create configurations for different use cases
```json
[
  {
    "baseTagPath": "Production",
    "sourcePath": "backups/production",
    "exportMode": "singleFile"
  },
  {
    "baseTagPath": "Production",
    "sourcePath": "development/production", 
    "exportMode": "individualFiles"
  }
]
```

### Workflow Integration

**Development Workflow**:
1. Work on specific tag areas
2. Use Selective Export to export just those tags
3. Commit changes to version control
4. Deploy using standard configurations

**Troubleshooting Workflow**:
1. Identify problematic tag areas
2. Export affected tags for analysis
3. Compare with known good versions
4. Re-import fixed configurations

## Troubleshooting

### No Matching Configurations

**Problem**: No configurations appear in the matching panel

**Solutions**:
- Check that configurations exist for the selected provider
- Verify your selection is within a configured `baseTagPath`
- Ensure configurations are properly saved in the Tag Configuration Manager

### Unexpected Results

**Problem**: Export includes more/fewer tags than expected

**Solutions**:
- Review the `baseTagPath` of matching configurations
- Check the export mode settings of matching configurations
- Use the standard export dialog for more precise control

### Performance Considerations

**Large Selections**: When selecting high-level folders:
- Review matching configurations before exporting
- Consider the combined size of all matching exports
- Monitor gateway resources during large operations

## Related Features

- **[Tag Configuration Manager](../designer/ui-components#tag-configuration-manager-dialog)**: Create and manage the configurations used by Selective Export
- **[Export Modes](./export-modes)**: Understand how different export modes affect Selective Export results
- **[Standard Export](./export-modes)**: Use when you need precise control over configuration selection