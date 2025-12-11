package dev.bwdesigngroup.ignition.tag_cicd.designer.actions;

import dev.bwdesigngroup.ignition.tag_cicd.designer.dialog.SelectiveTagExportDialog;
import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.designer.model.DesignerContext;

import javax.swing.*;
import java.awt.event.ActionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.inductiveautomation.ignition.common.BundleUtil.i18n;

public class SelectiveTagExportAction extends BaseAction {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DesignerContext context;

    public SelectiveTagExportAction(DesignerContext context, Icon icon) {
        super(i18n("tagcicd.Action.SelectiveExportTags.Name"), icon);
        this.context = context;
        putValue(SHORT_DESCRIPTION, i18n("tagcicd.Action.SelectiveExportTags.Description"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SelectiveTagExportDialog dialog = new SelectiveTagExportDialog(context);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            logger.debug("Selective tag export canceled by user");
            return;
        }

        logger.info("Selective export operation completed successfully");
    }
}