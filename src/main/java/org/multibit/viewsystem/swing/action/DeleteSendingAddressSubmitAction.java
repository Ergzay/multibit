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

import javax.swing.Action;
import javax.swing.JTable;

import org.multibit.controller.MultiBitController;
import org.multibit.model.AddressBookData;
import org.multibit.model.PerWalletModelData;
import org.multibit.model.WalletInfo;
import org.multibit.model.WalletVersion;
import org.multibit.utils.ImageLoader;
import org.multibit.viewsystem.swing.view.AddressBookTableModel;
import org.multibit.viewsystem.swing.view.dialogs.DeleteSendingAddressConfirmDialog;
import org.multibit.viewsystem.swing.view.panels.SendBitcoinPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link Action} represents an action to delete a sending address.
 */
public class DeleteSendingAddressSubmitAction extends MultiBitSubmitAction {

    private static final long serialVersionUID = 200111999465875405L;

    private static final Logger log = LoggerFactory.getLogger(DeleteSendingAddressSubmitAction.class);

    private SendBitcoinPanel sendBitcoinPanel;
    private DeleteSendingAddressConfirmDialog deleteSendingAddressConfirmDialog;

    /**
     * Creates a new {@link DeleteSendingAddressSubmitAction}.
     */
    public DeleteSendingAddressSubmitAction(MultiBitController controller, SendBitcoinPanel sendBitcoinPanel, DeleteSendingAddressConfirmDialog deleteSendingAddressConfirmDialog) {
        super(controller, "deleteSendingAddressSubmitAction.text", "deleteSendingAddressSubmitAction.tooltip",
                "deleteSendingAddressSubmitAction.mnemonicKey", ImageLoader.createImageIcon(ImageLoader.DELETE_ADDRESS_ICON_FILE));
        this.sendBitcoinPanel = sendBitcoinPanel;
        this.deleteSendingAddressConfirmDialog = deleteSendingAddressConfirmDialog;
    }

    /**
     * Delete the currently selected sending address.
     */
    public void actionPerformed(ActionEvent e) {
        if (abort()) {
            return;
        }

        PerWalletModelData perWalletModelData = controller.getModel().getActivePerWalletModelData();

        WalletInfo walletInfo = perWalletModelData.getWalletInfo();
        if (walletInfo == null) {
            walletInfo = new WalletInfo(perWalletModelData.getWalletFilename(), WalletVersion.PROTOBUF);
            perWalletModelData.setWalletInfo(walletInfo);
        }

        if (walletInfo.getSendingAddresses().size() > 0) {
            JTable addressesTable = sendBitcoinPanel.getAddressesTable();
            AddressBookTableModel addressesTableModel = sendBitcoinPanel.getAddressesTableModel();
            int viewRow = addressesTable.getSelectedRow();
            if (viewRow >= 0) {
                int selectedAddressRowModel = addressesTable.convertRowIndexToModel(viewRow);
                AddressBookData rowData = addressesTableModel.getAddressBookDataByRow(selectedAddressRowModel, false);
                if (rowData != null) {
                    if (selectedAddressRowModel < addressesTableModel.getRowCount()) {
                        walletInfo.getSendingAddresses().remove(rowData);
                        controller.getModel().getActivePerWalletModelData().setDirty(true);
                        addressesTableModel.fireTableDataChanged();
                    } else {
                        log.error("Could not remove row " + selectedAddressRowModel + " as table model only contained " + addressesTableModel.getRowCount() + " rows");
                    }
                    
                    int newViewRowToSelect = viewRow == 0 ? 0 : viewRow - 1;
                    if (addressesTableModel.getRowCount() > 0) {
                        int newModelRowtoSelect = addressesTable.convertRowIndexToModel(newViewRowToSelect);
                        AddressBookData newRowData = addressesTableModel.getAddressBookDataByRow(newModelRowtoSelect, false);
                    
                        controller.getModel().setActiveWalletPreference(sendBitcoinPanel.getAddressConstant(),
                                newRowData.getAddress());
                        controller.getModel().setActiveWalletPreference(sendBitcoinPanel.getLabelConstant(),
                                newRowData.getLabel());

                        if (sendBitcoinPanel.getAddressTextField() != null) {
                            sendBitcoinPanel.getAddressTextField().setText(newRowData.getAddress());
                        }
                        sendBitcoinPanel.getLabelTextArea().setText(newRowData.getLabel());

                        sendBitcoinPanel.displayQRCode(newRowData.getAddress(), sendBitcoinPanel.getAmount(), newRowData.getLabel());
                    }
                }
            }     
        }
        
        sendBitcoinPanel.checkDeleteSendingEnabled();
        
        if (deleteSendingAddressConfirmDialog != null) {
            deleteSendingAddressConfirmDialog.setVisible(false);
        }
        
        controller.displayView(controller.getCurrentView());
    }
}