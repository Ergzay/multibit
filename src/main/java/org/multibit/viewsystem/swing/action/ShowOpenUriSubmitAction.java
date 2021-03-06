/**
 * Copyright 2011 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multibit.viewsystem.swing.action;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.multibit.controller.MultiBitController;
import org.multibit.model.MultiBitModel;
import org.multibit.model.PerWalletModelData;
import org.multibit.viewsystem.View;
import org.multibit.viewsystem.dataproviders.ShowUriDialogDataProvider;
import org.multibit.viewsystem.swing.MultiBitFrame;
import org.multibit.viewsystem.swing.view.dialogs.ShowOpenUriDialog;

/**
 * This {@link Action} processes the open uri command
 */
public class ShowOpenUriSubmitAction extends AbstractAction {

    private static final long serialVersionUID = 1913592460523457765L;

    private MultiBitController controller;
    private ShowUriDialogDataProvider dataProvider;
    private ShowOpenUriDialog showOpenUriDialog;

    /**
     * Creates a new {@link ShowOpenUriSubmitAction}.
     */
    public ShowOpenUriSubmitAction(MultiBitFrame mainFrame, MultiBitController controller, ShowUriDialogDataProvider dataProvider,
            ShowOpenUriDialog showOpenUriDialog) {
        super(controller.getLocaliser().getString("showOpenUriView.yesText"));
        this.controller = controller;
        this.dataProvider = dataProvider;
        this.showOpenUriDialog = showOpenUriDialog;

        MnemonicUtil mnemonicUtil = new MnemonicUtil(controller.getLocaliser());

        putValue(SHORT_DESCRIPTION, controller.getLocaliser().getString("showOpenUriViewAction.tooltip"));
        putValue(MNEMONIC_KEY, mnemonicUtil.getMnemonic("showOpenUriViewAction.mnemonicKey"));
    }

    /**
     * show the showOpenUriDialog
     */
    public void actionPerformed(ActionEvent event) {
        // check to see if the wallet files have changed
        PerWalletModelData perWalletModelData = controller.getModel().getActivePerWalletModelData();
        boolean haveFilesChanged = controller.getFileHandler().haveFilesChanged(perWalletModelData);

        if (haveFilesChanged) {
            // set on the perWalletModelData that files have changed and fire
            // data changed
            perWalletModelData.setFilesHaveBeenChangedByAnotherProcess(true);
            controller.fireFilesHaveBeenChangedByAnotherProcess(perWalletModelData);
        } else {
            // get the data out of the temporary data and put it in the wallet
            // preferences

            String sendAddress = dataProvider.getAddress();
            String sendLabel = dataProvider.getLabel();
            String sendAmount = dataProvider.getAmount();
            boolean showDialog = dataProvider.isShowUriDialog();

            if (sendAddress != null) {
                controller.getModel().setActiveWalletPreference(MultiBitModel.SEND_ADDRESS, sendAddress);
            }
            if (sendLabel != null) {
                controller.getModel().setActiveWalletPreference(MultiBitModel.SEND_LABEL, sendLabel);
            }
            if (sendAmount != null) {
                controller.getModel().setActiveWalletPreference(MultiBitModel.SEND_AMOUNT, sendAmount);
            }
            
            // we want the send view to paste in the send data
            controller.getModel().setActiveWalletPreference(MultiBitModel.SEND_PERFORM_PASTE_NOW, "true");

            // we want to set the user preference to use the uri as the user
            // clicked yes
            controller.getModel().setUserPreference(MultiBitModel.OPEN_URI_USE_URI, "true");

            // save as user preference whether to show dialog or not
            controller.getModel().setUserPreference(MultiBitModel.OPEN_URI_SHOW_DIALOG, (new Boolean(showDialog)).toString());

            showOpenUriDialog.setVisible(false);
            controller.displayView(View.SEND_BITCOIN_VIEW);
        }
    }
}