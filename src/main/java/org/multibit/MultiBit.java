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
package org.multibit;

import java.awt.Cursor;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import org.multibit.controller.MultiBitController;
import org.multibit.exchange.CurrencyConverter;
import org.multibit.file.FileHandler;
import org.multibit.file.WalletLoadException;
import org.multibit.file.WalletVersionException;
import org.multibit.message.Message;
import org.multibit.message.MessageManager;
import org.multibit.model.MultiBitModel;
import org.multibit.model.PerWalletModelData;
import org.multibit.network.AlertManager;
import org.multibit.network.MultiBitService;
import org.multibit.platform.GenericApplication;
import org.multibit.platform.GenericApplicationFactory;
import org.multibit.platform.GenericApplicationSpecification;
import org.multibit.platform.listener.GenericOpenURIEvent;
import org.multibit.viewsystem.DisplayHint;
import org.multibit.viewsystem.ViewSystem;
import org.multibit.viewsystem.swing.ColorAndFontConstants;
import org.multibit.viewsystem.swing.MultiBitFrame;
import org.multibit.viewsystem.swing.action.ExitAction;
import org.multibit.viewsystem.swing.action.MigrateWalletsAction;
import org.multibit.viewsystem.swing.view.components.FontSizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main MultiBit entry class.
 * 
 * @author jim
 */
public class MultiBit {
    private static final Logger log = LoggerFactory.getLogger(MultiBit.class);

    private static MultiBitController controller = null;
    
    public static MultiBitController getController() {
        return controller;
    }
    
    /**
     * Used in testing
     */
    public static void setController(MultiBitController controller) {
        MultiBit.controller = controller;
    }
    
    /**
     * Start multibit user interface.
     * 
     * @param args    String encoding of arguments ([0]= Bitcoin URI)
     */
    @SuppressWarnings("deprecation")
    public static void main(String args[]) {
        // Enclosing try to enable graceful closure for unexpected errors.
        try {
            log.debug("Starting MultiBit at " + (new Date()).toGMTString());
            
            // Set any bespoke system properties
            try {
                // Fix for Windows / Java 7 / VPN bug.
                System.setProperty("java.net.preferIPv4Stack", "true");
            } catch (SecurityException se) {
                log.error(se.getClass().getName() + " " + se.getMessage());
            }
            
            ViewSystem swingViewSystem = null;

            ApplicationDataDirectoryLocator applicationDataDirectoryLocator = new ApplicationDataDirectoryLocator();

            // Load up the user preferences.
            Properties userPreferences = FileHandler.loadUserPreferences(applicationDataDirectoryLocator);

            // Create the controller.
            controller = new MultiBitController(applicationDataDirectoryLocator);

            log.info("Configuring native event handling");
            GenericApplicationSpecification specification = new GenericApplicationSpecification();
            specification.getOpenURIEventListeners().add(controller);
            specification.getPreferencesEventListeners().add(controller);
            specification.getAboutEventListeners().add(controller);
            specification.getQuitEventListeners().add(controller);
            GenericApplication genericApplication = GenericApplicationFactory.INSTANCE.buildGenericApplication(specification);

            log.debug("Checking to see if this is the primary MultiBit instance");
            String rawURI = null;
            if (args != null && args.length > 0) {
               rawURI = args[0];
            }
            if (!ApplicationInstanceManager.registerInstance(rawURI)) {
                // Instance already running.
                log.debug("Another instance of MultiBit is already running.  Exiting.");
                System.exit(0);
            }

            final MultiBitController finalController = controller;
            ApplicationInstanceManager.setApplicationInstanceListener(new ApplicationInstanceListener() {
                public void newInstanceCreated(String rawURI) {
                    final String finalRawUri = rawURI;
                    log.info("New instance of MultiBit detected...");
                    Runnable doProcessCommandLine = new Runnable() {
                        public void run() {
                            processCommandLineURI(finalController, finalRawUri);
                        }
                    };

                    SwingUtilities.invokeLater(doProcessCommandLine);
                }
            });

            Localiser localiser;
            String userLanguageCode = userPreferences.getProperty(MultiBitModel.USER_LANGUAGE_CODE);
            log.debug("userLanguageCode = {}", userLanguageCode);

            if (userLanguageCode == null) {
                // Initial localiser - no language info supplied - see if we can use the user default, else Localiser will set it to English.
                localiser = new Localiser(Locale.getDefault());
            
                userPreferences.setProperty(MultiBitModel.USER_LANGUAGE_CODE, localiser.getLocale().getLanguage());
            } else {
                if (MultiBitModel.USER_LANGUAGE_IS_DEFAULT.equals(userLanguageCode)) {
                    localiser = new Localiser(Locale.getDefault());
                } else {
                    localiser = new Localiser(new Locale(userLanguageCode));
                }
            }
            controller.setLocaliser(localiser);

            log.debug("Creating model");
            @SuppressWarnings("unused")
            MultiBitModel model = new MultiBitModel(controller, userPreferences);
            
            log.debug("Setting look and feel");
            try {
                String lookAndFeel = userPreferences.getProperty(MultiBitModel.LOOK_AND_FEEL);
 
                // No need to load look and feel if system - will be used by default.
                if (!"system".equalsIgnoreCase(lookAndFeel)) {
                    if (lookAndFeel != null && lookAndFeel != "") {
                        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                            if (lookAndFeel.equalsIgnoreCase(info.getName())) {
                                UIManager.setLookAndFeel(info.getClassName());
                                break;
                            }
                        }
                    }
                }
            } catch (UnsupportedLookAndFeelException e) {
                // carry on
            } catch (ClassNotFoundException e) {
                // carry on
            } catch (InstantiationException e) {
                // carry on
            } catch (IllegalAccessException e) {
                // carry on
            }

            // Initialise singletons.
            ColorAndFontConstants.init();
            FontSizer.INSTANCE.initialise(controller);
            CurrencyConverter.INSTANCE.initialise(finalController);

            log.debug("Creating user interface");
            swingViewSystem = new MultiBitFrame(controller, genericApplication, controller.getCurrentView());

            log.debug("Registering with controller");
            controller.registerViewSystem(swingViewSystem);

            log.debug("Creating Bitcoin service");
            // Create the MultiBitService that connects to the bitcoin network.
            MultiBitService multiBitService = new MultiBitService(controller);
            controller.setMultiBitService(multiBitService);

            log.debug("Locating wallets");
            // Find the active wallet filename in the multibit.properties.
            String activeWalletFilename = userPreferences.getProperty(MultiBitModel.ACTIVE_WALLET_FILENAME);

            // Load up all the wallets.
            String numberOfWalletsAsString = userPreferences.getProperty(MultiBitModel.NUMBER_OF_WALLETS);
            log.debug("When loading wallets, there were " + numberOfWalletsAsString);

            boolean useFastCatchup = false;
        
            if (numberOfWalletsAsString == null || "".equals(numberOfWalletsAsString)) {
                // If this is missing then there is just the one wallet (old format
                // properties or user has just started up for the first time).
                useFastCatchup = true;
                boolean thereWasAnErrorLoadingTheWallet = false;

                try {
                    // ActiveWalletFilename may be null on first time startup.
                    controller.addWalletFromFilename(activeWalletFilename);
                    List<PerWalletModelData> perWalletModelDataList = controller.getModel().getPerWalletModelDataList();
                    if (perWalletModelDataList != null && !perWalletModelDataList.isEmpty()) {
                        activeWalletFilename = perWalletModelDataList.get(0).getWalletFilename();
                        controller.getModel().setActiveWalletByFilename(activeWalletFilename);
                        log.debug("Created/loaded wallet '" + activeWalletFilename + "'");
                        MessageManager.INSTANCE.addMessage(new Message(controller.getLocaliser().getString(
                                "multiBit.createdWallet", new Object[] { activeWalletFilename })));
                    }
                } catch (WalletLoadException e) {
                    String message = controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                            new Object[] { activeWalletFilename, e.getMessage() });
                    MessageManager.INSTANCE.addMessage(new Message(message));
                    log.error(message);
                    thereWasAnErrorLoadingTheWallet = true;
                } catch (WalletVersionException e) {
                    String message = controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                            new Object[] { activeWalletFilename, e.getMessage() });
                    MessageManager.INSTANCE.addMessage(new Message(message));
                    log.error(message);
                    thereWasAnErrorLoadingTheWallet = true;
                } catch (IOException e) {
                    String message = controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                            new Object[] { activeWalletFilename, e.getMessage() });
                    MessageManager.INSTANCE.addMessage(new Message(message));
                    log.error(message);
                    thereWasAnErrorLoadingTheWallet = true;
                } finally {
                    if (thereWasAnErrorLoadingTheWallet) {
                        // Clear the backup wallet filename - this prevents it being automatically overwritten.
                        if (controller.getModel().getActiveWalletWalletInfo() != null) {
                            controller.getModel().getActiveWalletWalletInfo().put(MultiBitModel.WALLET_BACKUP_FILE, "");
                        }
                    }
                    if (swingViewSystem instanceof MultiBitFrame) {
                        ((MultiBitFrame) swingViewSystem).getWalletsView().initUI();
                        ((MultiBitFrame) swingViewSystem).getWalletsView().displayView(DisplayHint.COMPLETE_REDRAW);
                    }
                    controller.fireDataChangedUpdateNow();
                }
            } else {
                try {
                    int numberOfWallets = Integer.parseInt(numberOfWalletsAsString);

                    if (numberOfWallets > 0) {
                        ((MultiBitFrame) swingViewSystem).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                        for (int i = 1; i <= numberOfWallets; i++) {
                            boolean thereWasAnErrorLoadingTheWallet = false;

                            // Load up ith wallet filename.
                            String loopWalletFilename = userPreferences.getProperty(MultiBitModel.WALLET_FILENAME_PREFIX + i);
                            log.debug("Loading wallet from '{}'", loopWalletFilename);
                            Message message = new Message(controller.getLocaliser().getString("multiBit.openingWallet",
                                    new Object[] { loopWalletFilename }));
                            message.setShowInStatusBar(false);
                            MessageManager.INSTANCE.addMessage(message);
                            try {
                                if (activeWalletFilename != null && activeWalletFilename.equals(loopWalletFilename)) {
                                    controller.addWalletFromFilename(loopWalletFilename);
                                    controller.getModel().setActiveWalletByFilename(loopWalletFilename);
                                } else {
                                    controller.addWalletFromFilename(loopWalletFilename);
                                }
                                Message message2 = new Message(controller.getLocaliser().getString("multiBit.openingWalletIsDone",
                                        new Object[] { loopWalletFilename }));
                                message2.setShowInStatusBar(false);
                                MessageManager.INSTANCE.addMessage(message2);
                            } catch (WalletLoadException e) {
                                message = new Message( controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                                        new Object[] { loopWalletFilename, e.getMessage() }));
                                MessageManager.INSTANCE.addMessage(message);
                                log.error(message.getText());
                                thereWasAnErrorLoadingTheWallet = true;
                            } catch (WalletVersionException e) {
                                message = new Message( controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                                        new Object[] { loopWalletFilename, e.getMessage() }));
                                MessageManager.INSTANCE.addMessage(message);
                                log.error(message.getText());
                                thereWasAnErrorLoadingTheWallet = true;
                            } catch (IOException e) {
                                message = new Message( controller.getLocaliser().getString("openWalletSubmitAction.walletNotLoaded",
                                        new Object[] { loopWalletFilename, e.getMessage() }));
                                MessageManager.INSTANCE.addMessage(message);
                                log.error(message.getText());
                                thereWasAnErrorLoadingTheWallet = true;
                            }
                            
                            if (thereWasAnErrorLoadingTheWallet) {
                                PerWalletModelData loopData = controller.getModel().getPerWalletModelDataByWalletFilename(loopWalletFilename);
                                if (loopData != null) {
                                    // Clear the backup wallet filename - this prevents it being automatically overwritten.
                                    if (loopData.getWalletInfo() != null) {
                                        loopData.getWalletInfo().put(MultiBitModel.WALLET_BACKUP_FILE, "");
                                    }
                                }
                            }
                        }
                    }
 
                } catch (NumberFormatException nfe) {
                    // Carry on.
                } finally {
                    if (swingViewSystem instanceof MultiBitFrame) {
                        ((MultiBitFrame) swingViewSystem).getWalletsView().initUI();
                        ((MultiBitFrame) swingViewSystem).getWalletsView().displayView(DisplayHint.COMPLETE_REDRAW);
                    }
                    controller.fireDataChangedUpdateNow();
                    
                    ((MultiBitFrame) swingViewSystem).setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }

            log.debug("Checking for Bitcoin URI on command line");
            // Check for a valid entry on the command line (protocol handler).
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    log.debug("Started with args[{}]: '{}'", i, args[i]);
                }
                processCommandLineURI(controller, args[0]);
            } else {
                log.debug("No Bitcoin URI provided as an argument");
            }

            // Indicate to the application that startup has completed.
            controller.setApplicationStarting(false);

            // Check for any pending URI operations.
            controller.handleOpenURI();

            // Check if any wallets need migrating from serialised to protobuf.
            MigrateWalletsAction migrateWalletsAction = new MigrateWalletsAction(controller, (MultiBitFrame) swingViewSystem);
            migrateWalletsAction.actionPerformed(null);

            // Check to see if there is a new version.
            AlertManager.INSTANCE.initialise(controller, (MultiBitFrame) swingViewSystem);
            AlertManager.INSTANCE.checkVersion();
        
            log.debug("Downloading blockchain");
            if (useFastCatchup) {
                long earliestTimeSecs = controller.getModel().getActiveWallet().getEarliestKeyCreationTime();
                controller.getMultiBitService().getPeerGroup().setFastCatchupTimeSecs(earliestTimeSecs);
                log.debug("Using FastCatchup for blockchain sync with time of " + (new Date(earliestTimeSecs)).toString());
            }
            multiBitService.downloadBlockChain();
        } catch (Exception e) {
            // An unexcepted, unrecoverable error occurred.
            e.printStackTrace();
            
            log.error("An unexpected error caused MultiBit to quit.");
            log.error("The error was '" + e.getClass().getCanonicalName() + " " + e.getMessage() + "'");
            e.printStackTrace();
            log.error("Please read http://multibit.org/help_troubleshooting.html for help on troubleshooting.");
            
            // Try saving any dirty wallets.
            if (controller != null) {
                ExitAction exitAction = new ExitAction(controller, null);
                exitAction.actionPerformed(null);
            }
        }
    }

    static void processCommandLineURI(MultiBitController controller, String rawURI) {
        try {
            // Attempt to detect if the command line URI is valid
            // Note that this is largely because IE6-8 strip URL encoding
            // when passing in
            // URIs to a protocol handler
            // However, there is also the chance that anyone could
            // hand-craft a URI and pass
            // it in with non-ASCII character encoding present in the label
            // This a really limited approach (no consideration of
            // "amount=10.0&label=Black & White")
            // but should be OK for early use cases.
            int queryParamIndex = rawURI.indexOf("?");
            if (queryParamIndex > 0 && !rawURI.contains("%")) {
                // Possibly encoded but more likely not.
                String encodedQueryParams = URLEncoder.encode(rawURI.substring(queryParamIndex + 1), "UTF-8");
                rawURI = rawURI.substring(0, queryParamIndex) + "?" + encodedQueryParams;
                rawURI = rawURI.replaceAll("%3D", "=");
                rawURI = rawURI.replaceAll("%26", "&");
            }
            final URI uri;
            log.debug("Working with '{}' as a Bitcoin URI", rawURI);
            // Construct an OpenURIEvent to simulate receiving this from a listener
            uri = new URI(rawURI);
            GenericOpenURIEvent event = new GenericOpenURIEvent() {
                @Override
                public URI getURI() {
                    return uri;
                }
            };
            controller.displayView(controller.getCurrentView());
            // Call the event which will attempt validation against the Bitcoin URI specification.
            controller.onOpenURIEvent(event);
        } catch (URISyntaxException e) {
            log.error("URI is malformed. Received: '{}'", rawURI);
        } catch (UnsupportedEncodingException e) {
            log.error("UTF=8 is not supported on this platform");
        }
    }
}
