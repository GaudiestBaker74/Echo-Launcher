package launcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import java.io.StringWriter;
import java.nio.file.StandardCopyOption;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.awt.Desktop;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class LauncherUI extends Application {

    private static PrintWriter logWriter;
    private ComboBox<VersionEntry> versionBox;
    private TextField usernameField;
    private Slider ramSlider;
    private Label statusLabel;
    private ProgressBar progressBar;
    private List<VersionEntry> allVersions;
    private ImageView avatarView;
    private ComboBox<Profile> profileBox;
    private List<Profile> profiles = new ArrayList<Profile>();
    private boolean applyingProfile = false;
    private File selectedSkinFile;
    private File selectedCapeFile;
    private ComboBox<Instance> instanceBox;
    private List<Instance> instances = new ArrayList<Instance>();
    private ListView<Instance> instanceListView;
    private VBox instanceCardsBox;
    private Instance selectedInstance;
    private Label heroInstanceIconLabel;
    private Label heroInstanceNameLabel;
    private Label heroInstanceMetaLabel;
    private Label heroInstanceModsLabel;
    private Label heroUserLabel;
    private Button topInstanceBtn;
    private Label topUserLabel;
    private Label dashboardLastPlayedLabel;
    private Label dashboardPlayTimeLabel;
    private Label dashboardContentStatsLabel;
    private Label dashboardGameStatusLabel;
    private long currentSessionStartMillis = 0;
    private StackPane appOverlay;
    private VBox toastContainer;
    private StackPane dropOverlay;

    private static final Map<String, Image> iconMemoryCache = new ConcurrentHashMap<String, Image>();

    private static final ExecutorService iconExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Modrinth-Icon-Loader");
            thread.setDaemon(true);
            return thread;
        }
    });

    private static class ContentProviderOption {
        String id;
        String name;
        String iconPath;
        String fallbackText;
        String fallbackStyleClass;

        ContentProviderOption(String id, String name, String iconPath, String fallbackText, String fallbackStyleClass) {
            this.id = id;
            this.name = name;
            this.iconPath = iconPath;
            this.fallbackText = fallbackText;
            this.fallbackStyleClass = fallbackStyleClass;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private ComboBox<ContentProviderOption> createProviderComboBox() {
        final ComboBox<ContentProviderOption> providerBox = new ComboBox<ContentProviderOption>();

        providerBox.getItems().addAll(
                new ContentProviderOption(
                        "modrinth",
                        "Modrinth",
                        "/icons/modrinth.png",
                        "M",
                        "provider-logo-modrinth"
                ),
                new ContentProviderOption(
                        "curseforge",
                        "CurseForge",
                        "/icons/curseforge.png",
                        "C",
                        "provider-logo-curseforge"
                )
        );

        providerBox.getSelectionModel().selectFirst();
        providerBox.setPrefWidth(170);
        providerBox.getStyleClass().add("provider-combo");

        providerBox.setCellFactory(new javafx.util.Callback<ListView<ContentProviderOption>, ListCell<ContentProviderOption>>() {
            @Override
            public ListCell<ContentProviderOption> call(ListView<ContentProviderOption> listView) {
                return new ListCell<ContentProviderOption>() {
                    @Override
                    protected void updateItem(ContentProviderOption item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        setText(null);
                        setGraphic(createProviderGraphic(item));
                    }
                };
            }
        });

        providerBox.setButtonCell(new ListCell<ContentProviderOption>() {
            @Override
            protected void updateItem(ContentProviderOption item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                setText(null);
                setGraphic(createProviderGraphic(item));
            }
        });

        return providerBox;
    }

    private HBox createProviderGraphic(ContentProviderOption provider) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        StackPane logoBox = new StackPane();
        logoBox.getStyleClass().add("provider-logo-box");
        logoBox.setMinSize(26, 26);
        logoBox.setPrefSize(26, 26);
        logoBox.setMaxSize(26, 26);

        boolean loadedImage = false;

        if (provider.iconPath != null && !provider.iconPath.trim().isEmpty()) {
            try {
                java.io.InputStream in = getClass().getResourceAsStream(provider.iconPath);

                if (in != null) {
                    ImageView imageView = new ImageView(new Image(in, 22, 22, true, true));
                    imageView.setFitWidth(22);
                    imageView.setFitHeight(22);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.getStyleClass().add("provider-logo-image");

                    logoBox.getChildren().add(imageView);
                    loadedImage = true;
                }
            } catch (Exception ignored) {
            }
        }

        if (!loadedImage) {
            Label fallback = new Label(provider.fallbackText);
            fallback.getStyleClass().add("provider-logo-fallback");
            fallback.getStyleClass().add(provider.fallbackStyleClass);
            logoBox.getChildren().add(fallback);
        }

        Label name = new Label(provider.name);
        name.getStyleClass().add("provider-name");

        box.getChildren().addAll(logoBox, name);

        return box;
    }

    private boolean isModrinthProvider(ContentProviderOption provider) {
        return provider == null || "modrinth".equalsIgnoreCase(provider.id);
    }

    private boolean isCurseForgeProvider(ContentProviderOption provider) {
        return provider != null && "curseforge".equalsIgnoreCase(provider.id);
    }

    private String getCurseForgeApiKey() {
        String key = prefs.get("curseforgeApiKey", "");

        if (key == null) {
            return "";
        }

        key = key.trim();

        // Por si el usuario pegó la clave con comillas.
        if ((key.startsWith("\"") && key.endsWith("\"")) ||
                (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }

        // Por si pegó algo tipo: Bearer XXXXX
        if (key.toLowerCase().startsWith("bearer ")) {
            key = key.substring("bearer ".length()).trim();
        }

        return key;
    }

    private boolean hasCurseForgeApiKey() {
        String key = getCurseForgeApiKey();
        return key != null && !key.trim().isEmpty();
    }

    private void saveCurseForgeApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            prefs.remove("curseforgeApiKey");
            return;
        }

        String key = apiKey.trim();

        if ((key.startsWith("\"") && key.endsWith("\"")) ||
                (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }

        if (key.toLowerCase().startsWith("bearer ")) {
            key = key.substring("bearer ".length()).trim();
        }

        prefs.put("curseforgeApiKey", key);
    }

    private boolean ensureCurseForgeApiKey() {
        if (hasCurseForgeApiKey()) {
            return true;
        }

        showCurseForgeApiKeyDialog();

        return hasCurseForgeApiKey();
    }

    private void showCurseForgeApiKeyDialog() {
        final Dialog<String> dialog = new Dialog<String>();
        dialog.setTitle("CurseForge API Key");
        dialog.setHeaderText("Configurar API Key de CurseForge");

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType removeButton = new ButtonType("Eliminar clave", ButtonBar.ButtonData.LEFT);
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(saveButton, removeButton, cancelButton);

        VBox root = new VBox(12);
        root.setPadding(new Insets(10));
        root.setPrefWidth(480);

        Label info = new Label(
                "Introduce tu API Key oficial de CurseForge.\n\n" +
                        "Esta clave será necesaria para buscar y descargar contenido desde CurseForge."
        );
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px;");

        final PasswordField keyField = new PasswordField();
        keyField.setPromptText("CurseForge API Key");

        String currentKey = getCurseForgeApiKey();

        if (currentKey != null && !currentKey.isEmpty()) {
            keyField.setText(currentKey);
        }

        final TextField visibleKeyField = new TextField();
        visibleKeyField.setPromptText("CurseForge API Key");
        visibleKeyField.setManaged(false);
        visibleKeyField.setVisible(false);

        visibleKeyField.textProperty().bindBidirectional(keyField.textProperty());

        CheckBox showKeyCheck = new CheckBox("Mostrar clave");
        showKeyCheck.setStyle("-fx-text-fill: #374151;");

        showKeyCheck.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                boolean show = newValue != null && newValue;

                visibleKeyField.setVisible(show);
                visibleKeyField.setManaged(show);

                keyField.setVisible(!show);
                keyField.setManaged(!show);
            }
        });

        Label warning = new Label(
                "No compartas esta clave públicamente."
        );
        warning.setWrapText(true);
        warning.setStyle("-fx-text-fill: #b45309; -fx-font-size: 12px;");

        root.getChildren().addAll(info, keyField, visibleKeyField, showKeyCheck, warning);

        dialog.getDialogPane().setContent(root);

        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setResultConverter(new javafx.util.Callback<ButtonType, String>() {
            @Override
            public String call(ButtonType buttonType) {
                if (buttonType == saveButton) {
                    return keyField.getText();
                }

                if (buttonType == removeButton) {
                    return "__REMOVE__";
                }

                return null;
            }
        });

        java.util.Optional<String> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return;
        }

        String value = result.get();

        if ("__REMOVE__".equals(value)) {
            prefs.remove("curseforgeApiKey");
            statusLabel.setText("Clave de CurseForge eliminada.");
            return;
        }

        if (value == null || value.trim().isEmpty()) {
            statusLabel.setText("No se guardó ninguna clave de CurseForge.");
            return;
        }

        saveCurseForgeApiKey(value);

        try {
            CurseForgeClient.validateApiKey(getCurseForgeApiKey());
            statusLabel.setText("Clave de CurseForge guardada y validada correctamente.");
        } catch (Exception ex) {
            statusLabel.setText("Clave guardada, pero CurseForge la rechazó: " + ex.getMessage());
        }
    }

    private void loadProviderPopularContent(final ComboBox<ContentProviderOption> providerBox,
                                            final ComboBox<String> typeBox,
                                            final ListView<ModrinthClient.ModResult> resultsList,
                                            final Button searchBtn,
                                            final Button installBtn,
                                            final Label status) {
        final ContentProviderOption provider = providerBox.getValue();

        if (isCurseForgeProvider(provider)) {
            if (!hasCurseForgeApiKey()) {
                resultsList.getItems().clear();
                installBtn.setText("Instalar seleccionado");
                installBtn.setDisable(true);
                searchBtn.setDisable(false);

                status.setText("CurseForge seleccionado. Configura tu API Key para habilitar búsquedas.");
                return;
            }

            final String apiKey = getCurseForgeApiKey();
            final String type = getSelectedContentType(typeBox);

            resultsList.getItems().clear();
            installBtn.setText("Instalar seleccionado");
            installBtn.setDisable(true);
            searchBtn.setDisable(true);

            status.setText("Cargando contenido popular de CurseForge...");

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final List<ModrinthClient.ModResult> results =
                                CurseForgeClient.searchPopularProjects(apiKey, type);

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                resultsList.getItems().setAll(results);
                                searchBtn.setDisable(false);

                                if (results.isEmpty()) {
                                    status.setText("No se encontraron resultados en CurseForge.");
                                } else {
                                    status.setText("Mostrando " + results.size() + " resultados populares de CurseForge.");
                                }
                            }
                        });
                    } catch (final Exception ex) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                searchBtn.setDisable(false);
                                status.setText("Error cargando CurseForge: " + ex.getMessage());
                            }
                        });
                    }
                }
            }, "CurseForge-Popular");

            t.setDaemon(true);
            t.start();
            return;
        }

        // Modrinth
        final String type = getSelectedContentType(typeBox);

        resultsList.getItems().clear();
        installBtn.setText("Instalar seleccionado");
        installBtn.setDisable(true);
        searchBtn.setDisable(true);

        status.setText("Cargando contenido popular de Modrinth...");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ModrinthClient.ModResult> results =
                            ModrinthClient.searchPopularProjects(type);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            resultsList.getItems().setAll(results);
                            searchBtn.setDisable(false);

                            if (results.isEmpty()) {
                                status.setText("No se encontraron resultados en Modrinth.");
                            } else {
                                status.setText("Mostrando " + results.size() + " resultados populares de Modrinth.");
                            }
                        }
                    });
                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            searchBtn.setDisable(false);
                            status.setText("Error cargando Modrinth: " + ex.getMessage());
                        }
                    });
                }
            }
        }, "Modrinth-Popular");

        t.setDaemon(true);
        t.start();
    }

    private final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(LauncherUI.class);

    @Override
    public void start(Stage stage) {
        try {
            // Initialize log file
            File logDir = new File(System.getProperty("user.home"), ".minecraft-launcher");
            logDir.mkdirs();
            File logFile = new File(logDir, "launcher.log");
            logWriter = new PrintWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(logFile, false),
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            );
            logWriter.println("=== Launcher started at " + java.time.LocalDateTime.now() + " ===");
            logWriter.flush();
            
            // Set up global exception handler
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                logWriter.println("UNCAUGHT EXCEPTION in thread " + t.getName() + ":");
                e.printStackTrace(logWriter);
                logWriter.flush();
            });

            BorderPane root = new BorderPane();
            root.getStyleClass().add("root");

            appOverlay = new StackPane();
            appOverlay.getChildren().add(root);

            toastContainer = new VBox(8);
            toastContainer.setMouseTransparent(true);
            toastContainer.getStyleClass().add("toast-container");

            StackPane.setAlignment(toastContainer, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(toastContainer, new Insets(18));

            appOverlay.getChildren().add(toastContainer);

            dropOverlay = new StackPane();
            dropOverlay.getStyleClass().add("drop-overlay");
            dropOverlay.setVisible(false);
            dropOverlay.setMouseTransparent(true);

            Label dropLabel = new Label("Suelta el modpack para importarlo");
            dropLabel.getStyleClass().add("drop-overlay-label");

            dropOverlay.getChildren().add(dropLabel);

            appOverlay.getChildren().add(dropOverlay);

            // --- LEFT PANEL (Profile & Settings) ---
            VBox leftPanel = new VBox(12);
            leftPanel.setPadding(new Insets(20));
            leftPanel.setPrefWidth(360);
            leftPanel.setMinWidth(360);
            leftPanel.setMaxWidth(360);
            leftPanel.getStyleClass().add("sidebar");
            leftPanel.setStyle(null);

            Label title = new Label("Minecraft");
            Label subtitle = new Label("Launcher");
            title.getStyleClass().add("app-title");
            subtitle.getStyleClass().add("app-subtitle");

            VBox titleBox = new VBox(-2, title, subtitle);
            titleBox.getStyleClass().add("brand-box");
            titleBox.setPadding(new Insets(0, 0, 10, 0));

        // Avatar & Username
        HBox userBox = new HBox(15);
        userBox.setAlignment(Pos.CENTER_LEFT);

        avatarView = new ImageView();
        avatarView.setFitWidth(64);
        avatarView.setFitHeight(64);

            VBox instancePane = new VBox(10);
            instancePane.getStyleClass().add("instance-panel");
            instancePane.setMaxWidth(Double.MAX_VALUE);
            instancePane.setFillWidth(true);

            HBox instanceHeader = new HBox(8);
            instanceHeader.setAlignment(Pos.CENTER_LEFT);

            Label instanceLabel = new Label("Instancias");
            instanceLabel.getStyleClass().add("header-label");

            Region instanceHeaderSpacer = new Region();
            HBox.setHgrow(instanceHeaderSpacer, Priority.ALWAYS);

            Button openInstanceFolderBtn = new Button("Carpeta");
            openInstanceFolderBtn.getStyleClass().add("secondary-button");

            instanceHeader.getChildren().addAll(instanceLabel, instanceHeaderSpacer, openInstanceFolderBtn);

            instanceCardsBox = new VBox(8);
            instanceCardsBox.getStyleClass().add("instance-cards-box");
            instanceCardsBox.setFillWidth(true);
            instanceCardsBox.setMaxWidth(Double.MAX_VALUE);

            HBox instanceActions1 = new HBox(8);

            Button newInstanceBtn = new Button("Nueva");
            newInstanceBtn.getStyleClass().add("button");
            newInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(newInstanceBtn, Priority.ALWAYS);

            Button saveInstanceBtn = new Button("Guardar");
            saveInstanceBtn.getStyleClass().add("secondary-button");
            saveInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(saveInstanceBtn, Priority.ALWAYS);

            instanceActions1.getChildren().addAll(newInstanceBtn, saveInstanceBtn);

            HBox instanceActions2 = new HBox(8);

            Button duplicateInstanceBtn = new Button("Duplicar");
            duplicateInstanceBtn.getStyleClass().add("secondary-button");
            duplicateInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(duplicateInstanceBtn, Priority.ALWAYS);

            Button deleteInstanceBtn = new Button("Eliminar");
            deleteInstanceBtn.getStyleClass().add("secondary-button");
            deleteInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(deleteInstanceBtn, Priority.ALWAYS);

            instanceActions2.getChildren().addAll(duplicateInstanceBtn, deleteInstanceBtn);

            HBox instanceActions3 = new HBox(8);

            Button editInstanceBtn = new Button("Editar");
            editInstanceBtn.getStyleClass().add("secondary-button");
            editInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(editInstanceBtn, Priority.ALWAYS);

            Button importInstanceBtn = new Button("Importar");
            importInstanceBtn.getStyleClass().add("secondary-button");
            importInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(importInstanceBtn, Priority.ALWAYS);

            Button exportInstanceBtn = new Button("Exportar");
            exportInstanceBtn.getStyleClass().add("secondary-button");
            exportInstanceBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(exportInstanceBtn, Priority.ALWAYS);

            instanceActions3.getChildren().addAll(editInstanceBtn, importInstanceBtn, exportInstanceBtn);

            instancePane.getChildren().addAll(
                    instanceHeader,
                    instanceCardsBox,
                    instanceActions1,
                    instanceActions2,
                    instanceActions3
            );

            newInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    createNewInstance();
                }
            });

            saveInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    saveSelectedInstance();
                }
            });

            duplicateInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    duplicateSelectedInstance();
                }
            });

            deleteInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    deleteSelectedInstanceLauncher();
                }
            });

            editInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showEditInstanceDialog();
                }
            });

            importInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    importInstanceFromZip();
                }
            });

            exportInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    exportCurrentInstanceToZip();
                }
            });

            openInstanceFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    try {
                        Instance instance = getCurrentInstance();

                        if (instance == null) {
                            statusLabel.setText("No hay instancia seleccionada.");
                            return;
                        }

                        File dir = InstanceManager.getGameDir(instance);
                        dir.mkdirs();

                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(dir);
                        }
                    } catch (Exception ex) {
                        statusLabel.setText("No se pudo abrir carpeta de instancia: " + ex.getMessage());
                    }
                }
            });

        // Wrap avatar to give it a border via CSS
        StackPane avatarContainer = new StackPane(avatarView);
        avatarContainer.getStyleClass().add("avatar-border");
        avatarContainer.setMaxSize(68, 68);

        usernameField = new TextField();
        usernameField.setPromptText("Nombre de Usuario");
        usernameField.getStyleClass().add("user-field");
        HBox.setHgrow(usernameField, Priority.ALWAYS);

            usernameField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> obs, String oldV, String newV) {
                    updateAvatar(newV);

                    String name = newV == null || newV.trim().isEmpty() ? "Steve" : newV.trim();

                    if (heroUserLabel != null) {
                        heroUserLabel.setText("Usuario: " + name);
                    }

                    if (topUserLabel != null) {
                        topUserLabel.setText(name);
                    }
                }
            });

        avatarContainer.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                show3DSkinViewer();
            }
        });
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);

        userBox.getChildren().addAll(avatarContainer, usernameField);

            // Profiles
            VBox profilesPane = new VBox(8);
            profilesPane.getStyleClass().add("profile-panel");

            Label profilesLabel = new Label("Perfil");
            profilesLabel.getStyleClass().add("header-label");

            profileBox = new ComboBox<Profile>();
            profileBox.setMaxWidth(Double.MAX_VALUE);
            profileBox.setPromptText("Seleccionar perfil");

            HBox profileActions = new HBox(8);

            Button newProfileBtn = new Button("Nuevo");
            newProfileBtn.getStyleClass().add("secondary-button");
            newProfileBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(newProfileBtn, Priority.ALWAYS);

            Button saveProfileBtn = new Button("Guardar");
            saveProfileBtn.getStyleClass().add("button");
            saveProfileBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(saveProfileBtn, Priority.ALWAYS);

            Button deleteProfileBtn = new Button("Eliminar");
            deleteProfileBtn.getStyleClass().add("secondary-button");
            deleteProfileBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(deleteProfileBtn, Priority.ALWAYS);

            profileActions.getChildren().addAll(newProfileBtn, saveProfileBtn, deleteProfileBtn);
            profilesPane.getChildren().addAll(profilesLabel, profileBox, profileActions);

            profileBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Profile>() {
                @Override
                public void changed(ObservableValue<? extends Profile> observable, Profile oldValue, Profile newValue) {
                    if (newValue != null && !applyingProfile) {
                        applyProfile(newValue);
                    }
                }
            });

            newProfileBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    createNewProfile();
                }
            });

            saveProfileBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    saveSelectedProfile();
                }
            });

            deleteProfileBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    deleteSelectedProfile();
                }
            });

        // RAM Slider
// RAM Card
            VBox ramBox = new VBox(10);
            ramBox.getStyleClass().add("ram-card");

            HBox ramHeader = new HBox(10);
            ramHeader.setAlignment(Pos.CENTER_LEFT);

            Label ramTitle = new Label("Memoria RAM");
            ramTitle.getStyleClass().add("ram-title");

            Region ramSpacer = new Region();
            HBox.setHgrow(ramSpacer, Priority.ALWAYS);

            final Label ramValueLabel = new Label("2 GB");
            ramValueLabel.getStyleClass().add("ram-value");

            ramHeader.getChildren().addAll(ramTitle, ramSpacer, ramValueLabel);

            Label ramHint = new Label("Recomendado: 4-6 GB para mods y shaders.");
            ramHint.getStyleClass().add("ram-hint");
            ramHint.setWrapText(true);

            ramSlider = new Slider(1, 16, 2);
            ramSlider.getStyleClass().add("ram-slider");
            ramSlider.setShowTickLabels(false);
            ramSlider.setShowTickMarks(false);
            ramSlider.setMajorTickUnit(1);
            ramSlider.setMinorTickCount(0);
            ramSlider.setBlockIncrement(1);
            ramSlider.setSnapToTicks(true);

            HBox ramScale = new HBox();
            ramScale.setAlignment(Pos.CENTER);

            Label ramMin = new Label("1 GB");
            ramMin.getStyleClass().add("ram-scale-label");

            Region ramScaleSpacer = new Region();
            HBox.setHgrow(ramScaleSpacer, Priority.ALWAYS);

            Label ramMax = new Label("16 GB");
            ramMax.getStyleClass().add("ram-scale-label");

            ramScale.getChildren().addAll(ramMin, ramScaleSpacer, ramMax);

            ramSlider.valueProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                    int ramGb = newValue.intValue();
                    ramValueLabel.setText(ramGb + " GB");

                    if (ramGb <= 2) {
                        ramHint.setText("Ligero: ideal para vanilla o pocos mods.");
                    } else if (ramGb <= 6) {
                        ramHint.setText("Recomendado: buena opción para Fabric, mods y shaders moderados.");
                    } else if (ramGb <= 10) {
                        ramHint.setText("Alto: útil para packs grandes o shaders pesados.");
                    } else {
                        ramHint.setText("Muy alto: úsalo solo si tu PC tiene bastante RAM libre.");
                    }
                }
            });

            ramBox.getChildren().addAll(ramHeader, ramSlider, ramScale, ramHint);

// Tools / Viewers
            Label toolsLabel = new Label("Herramientas");
            toolsLabel.getStyleClass().add("header-label");

            VBox toolsContainer = new VBox(10);
            toolsContainer.setMaxWidth(Double.MAX_VALUE);

            VBox modsCard = createToolCard(
                    "Mods",
                    "Gestiona los mods instalados",
                    "📦"
            );

            VBox searchModsCard = createToolCard(
                    "Buscar",
                    "Descarga mods de Modrinth",
                    "🔎"
            );

            VBox graphicsCard = createToolCard(
                    "Pack gráfico",
                    "Shaders, Sodium e Iris",
                    "✨"
            );

            VBox cosmeticsCard = createToolCard(
                    "Cosméticos",
                    "Skins, capas y visor 3D",
                    "🧍"
            );

            HBox toolsRow1 = new HBox(10);
            HBox toolsRow2 = new HBox(10);

            HBox.setHgrow(modsCard, Priority.ALWAYS);
            HBox.setHgrow(searchModsCard, Priority.ALWAYS);
            HBox.setHgrow(graphicsCard, Priority.ALWAYS);
            HBox.setHgrow(cosmeticsCard, Priority.ALWAYS);

            toolsRow1.getChildren().addAll(modsCard, searchModsCard);
            toolsRow2.getChildren().addAll(graphicsCard, cosmeticsCard);

            toolsContainer.getChildren().addAll(toolsRow1, toolsRow2);

            modsCard.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    showInstalledModsDialog();
                }
            });

            searchModsCard.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    showContentSearchDialog();
                }
            });

            graphicsCard.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    showGraphicsPackDialog();
                }
            });

            cosmeticsCard.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    showCosmeticsDialog();
                }
            });

        Region r1 = new Region();
        r1.setMinHeight(4);
        Region r2 = new Region();
        r2.setMinHeight(4);
            leftPanel.getChildren().addAll(
                    titleBox,
                    instancePane,
                    userBox,
                    r1,
                    ramBox,
                    r2,
                    toolsLabel,
                    toolsContainer
            );

        // --- RIGHT PANEL (Launch & Output) ---
// --- RIGHT PANEL (Modern Dashboard) ---
            HBox topBar = new HBox(12);
            topBar.getStyleClass().add("top-bar");
            topBar.setAlignment(Pos.CENTER_LEFT);
            topBar.setMaxWidth(Double.MAX_VALUE);

            VBox brandBox = new VBox(-2);

            Label brandTitle = new Label("Minecraft");
            brandTitle.getStyleClass().add("top-brand-title");

            Label brandSubtitle = new Label("Launcher");
            brandSubtitle.getStyleClass().add("top-brand-subtitle");

            brandBox.getChildren().addAll(brandTitle, brandSubtitle);

            Region topSpacer = new Region();
            HBox.setHgrow(topSpacer, Priority.ALWAYS);

            topUserLabel = new Label("Steve");
            topUserLabel.getStyleClass().add("top-user-pill");

            topInstanceBtn = new Button("Instancias");
            topInstanceBtn.getStyleClass().add("top-nav-button");

            Button topModsBtn = new Button("Mods");
            topModsBtn.getStyleClass().add("top-nav-button");

            Button topSearchBtn = new Button("Buscar");
            topSearchBtn.getStyleClass().add("top-nav-button");

            Button topGraphicsBtn = new Button("Gráficos");
            topGraphicsBtn.getStyleClass().add("top-nav-button");

            Button topSkinsBtn = new Button("Skins");
            topSkinsBtn.getStyleClass().add("top-nav-button");

            Button topWorldsBtn = new Button("Mundos");
            topWorldsBtn.getStyleClass().add("top-nav-button");

            Button topScreenshotsBtn = new Button("Capturas");
            topScreenshotsBtn.getStyleClass().add("top-nav-button");

            Button topSettingsBtn = new Button("Ajustes");
            topSettingsBtn.getStyleClass().add("top-nav-button");

            topInstanceBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showInstanceSwitcherDialog();
                }
            });

            topModsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showInstalledModsDialog();
                }
            });

            topSearchBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showContentSearchDialog();
                }
            });

            topGraphicsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showGraphicsPackDialog();
                }
            });

            topSkinsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showCosmeticsDialog();
                }
            });

            topWorldsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showWorldManagerDialog();
                }
            });

            topScreenshotsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showScreenshotsDialog();
                }
            });

            topBar.getChildren().addAll(
                    brandBox,
                    topSpacer,
                    topUserLabel,
                    topInstanceBtn,
                    topModsBtn,
                    topSearchBtn,
                    topGraphicsBtn,
                    topSkinsBtn,
                    topWorldsBtn,
                    topScreenshotsBtn,
                    topSettingsBtn
            );

            topSettingsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showSettingsDialog();
                }
            });

// ================= HERO CARD PREMIUM =================
            VBox heroCard = new VBox(16);
            heroCard.getStyleClass().add("premium-hero-card");
            heroCard.setMaxWidth(Double.MAX_VALUE);

            HBox heroTop = new HBox(16);
            heroTop.setAlignment(Pos.CENTER_LEFT);

            heroInstanceIconLabel = new Label("🌱");
            heroInstanceIconLabel.getStyleClass().add("premium-hero-icon");

            VBox heroTextBox = new VBox(5);
            HBox.setHgrow(heroTextBox, Priority.ALWAYS);

            Label heroSmallTitle = new Label("Instancia actual");
            heroSmallTitle.getStyleClass().add("hero-small-title");

            heroInstanceNameLabel = new Label("Principal");
            heroInstanceNameLabel.getStyleClass().add("premium-hero-title");

            heroInstanceMetaLabel = new Label("Sin versión · 2 GB RAM");
            heroInstanceMetaLabel.getStyleClass().add("premium-hero-meta");

            heroInstanceModsLabel = new Label("0 mods activos");
            heroInstanceModsLabel.getStyleClass().add("hero-pill");

            heroUserLabel = new Label("Usuario: Steve");
            heroUserLabel.getStyleClass().add("hero-user");

            heroTextBox.getChildren().addAll(
                    heroSmallTitle,
                    heroInstanceNameLabel,
                    heroInstanceMetaLabel,
                    heroInstanceModsLabel,
                    heroUserLabel
            );

            Button playButton = new Button("Jugar");
            playButton.getStyleClass().add("premium-play-button");
            playButton.setPrefWidth(130);
            playButton.setPrefHeight(46);
            playButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    launchGame();
                }
            });

            heroTop.getChildren().addAll(heroInstanceIconLabel, heroTextBox, playButton);

// Stats row
            HBox statsRow = new HBox(12);
            statsRow.setAlignment(Pos.CENTER_LEFT);

            VBox statusStat = createDashboardStatCard("Estado", "Listo");
            dashboardGameStatusLabel = (Label) statusStat.getChildren().get(1);

            VBox lastPlayedStat = createDashboardStatCard("Última vez", "Nunca");
            dashboardLastPlayedLabel = (Label) lastPlayedStat.getChildren().get(1);

            VBox playTimeStat = createDashboardStatCard("Tiempo jugado", "0 min");
            dashboardPlayTimeLabel = (Label) playTimeStat.getChildren().get(1);

            VBox contentStat = createDashboardStatCard("Contenido", "0 mods · 0 shaders · 0 texturas");
            dashboardContentStatsLabel = (Label) contentStat.getChildren().get(1);

            HBox.setHgrow(statusStat, Priority.ALWAYS);
            HBox.setHgrow(lastPlayedStat, Priority.ALWAYS);
            HBox.setHgrow(playTimeStat, Priority.ALWAYS);
            HBox.setHgrow(contentStat, Priority.ALWAYS);

            statsRow.getChildren().addAll(statusStat, lastPlayedStat, playTimeStat, contentStat);

            heroCard.getChildren().addAll(heroTop, statsRow);

// ================= VERSION CARD =================
            VBox versionCard = new VBox(12);
            versionCard.getStyleClass().add("dashboard-card");
            versionCard.setMaxWidth(Double.MAX_VALUE);

            HBox versionHead = new HBox(10);
            versionHead.setAlignment(Pos.CENTER_LEFT);

            Label versionLabel = new Label("Versión");
            versionLabel.getStyleClass().add("dashboard-card-title");

            Region versionSpacer = new Region();
            HBox.setHgrow(versionSpacer, Priority.ALWAYS);

            final ComboBox<String> versionFilterBox = new ComboBox<String>();
            versionFilterBox.getItems().addAll("Todas", "Instaladas", "Fabric", "Vanilla");
            versionFilterBox.getSelectionModel().selectFirst();
            versionFilterBox.setPrefWidth(130);

            final TextField searchField = new TextField();
            searchField.setPromptText("Buscar versión...");
            searchField.setPrefWidth(180);
            searchField.getStyleClass().add("dashboard-search");

            versionHead.getChildren().addAll(versionLabel, versionSpacer, versionFilterBox, searchField);

            HBox versionSelectBox = new HBox(10);
            versionSelectBox.setAlignment(Pos.CENTER_LEFT);

            versionBox = new ComboBox<VersionEntry>();
            versionBox.setMaxWidth(Double.MAX_VALUE);
            versionBox.setPromptText("Cargando versiones...");
            HBox.setHgrow(versionBox, Priority.ALWAYS);

            final Button fabricBtn = new Button("Fabric");
            fabricBtn.getStyleClass().add("secondary-button");

            final Button repairBtn = new Button("Reparar");
            repairBtn.getStyleClass().add("secondary-button");

            versionSelectBox.getChildren().addAll(versionBox, fabricBtn, repairBtn);

            versionCard.getChildren().addAll(versionHead, versionSelectBox);

// ================= STATUS CARD =================
            VBox statusCard = new VBox(8);
            statusCard.getStyleClass().add("dashboard-card");
            statusCard.setMaxWidth(Double.MAX_VALUE);

            Label statusTitle = new Label("Estado");
            statusTitle.getStyleClass().add("dashboard-card-title");

            statusLabel = new Label("Esperando...");
            statusLabel.getStyleClass().add("status-label");

            progressBar = new ProgressBar(0);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.setVisible(false);

            statusCard.getChildren().addAll(statusTitle, statusLabel, progressBar);

// ================= MINIMAL QUICK ACTIONS =================
            HBox quickActions = new HBox(8);
            quickActions.setAlignment(Pos.CENTER_LEFT);
            quickActions.getStyleClass().add("minimal-actions-bar");

            Button quickModsBtn = createMinimalActionButton("Mods");
            Button quickSearchBtn = createMinimalActionButton("Mercado");
            Button quickGraphicsBtn = createMinimalActionButton("Gráficos");
            Button quickEditBtn = createMinimalActionButton("Instancia");

            quickModsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showInstalledModsDialog();
                }
            });

            quickSearchBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showContentSearchDialog();
                }
            });

            quickGraphicsBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    showGraphicsPackDialog();
                }
            });

            quickEditBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    openEditInstanceSafely();
                }
            });

            quickActions.getChildren().addAll(
                    quickModsBtn,
                    quickSearchBtn,
                    quickGraphicsBtn,
                    quickEditBtn
            );

// ================= CONSOLE =================
            VBox rightPanel = new VBox(18);
            final TextArea logArea = new TextArea();
            logArea.setEditable(false);
            logArea.setWrapText(false);
            logArea.getStyleClass().add("console-output");
            VBox.setVgrow(logArea, Priority.ALWAYS);

            redirectLogs(logArea);

            TitledPane consolePane = new TitledPane("Consola del launcher", logArea);
            consolePane.getStyleClass().add("console-pane");
            consolePane.setExpanded(false);
            VBox.setVgrow(consolePane, Priority.ALWAYS);

            rightPanel.getChildren().addAll(
                    topBar,
                    heroCard,
                    versionCard,
                    consolePane
            );
            ScrollPane leftScroll = new ScrollPane(leftPanel);
            leftScroll.setFitToWidth(true);
            leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            leftScroll.setPrefWidth(360);
            leftScroll.setMinWidth(360);
            leftScroll.setMaxWidth(360);
            leftScroll.getStyleClass().add("left-scroll");

            root.setLeft(null);
            root.setCenter(rightPanel);

        // Scene
            Scene scene = new Scene(appOverlay, 680, 720);
            setupModpackDragAndDrop(scene);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load style.css");
        }

        stage.setScene(scene);
            stage.setTitle("Echo Launcher");
            stage.setMinWidth(980);
            stage.setMinHeight(620);
        stage.show();

        // Listeners for functionality
            ChangeListener<Object> versionFilterListener = new ChangeListener<Object>() {
                @Override
                public void changed(ObservableValue<?> observable, Object oldValue, Object newValue) {
                    applyVersionFilter(searchField.getText(), versionFilterBox.getValue());
                }
            };

            searchField.textProperty().addListener(versionFilterListener);
            versionFilterBox.valueProperty().addListener(versionFilterListener);

            repairBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    repairSelectedVersion(repairBtn);
                }
            });

            fabricBtn.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    final VersionEntry v = versionBox.getValue();

                    if (v == null) {
                        statusLabel.setText("⚠️ Selecciona una versión primero");
                        return;
                    }

                    showFabricLoaderSelectionDialog(v, fabricBtn);
                }
            });

// Mucho más abajo, al final de start:
            loadSettings();
            loadInstancesIntoCombo();
            loadProfilesIntoCombo();
            loadVersions();
        } catch (Exception e) {
            e.printStackTrace();

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de inicio");
            alert.setHeaderText("Error al iniciar el launcher");
            alert.setContentText(e.getMessage());

            TextArea area = new TextArea(sw.toString());
            area.setEditable(false);
            area.setWrapText(false);
            area.setPrefWidth(900);
            area.setPrefHeight(500);

            alert.getDialogPane().setExpandableContent(area);
            alert.getDialogPane().setExpanded(true);

            alert.showAndWait();
            System.exit(1);
        }
    }

    private void openInstanceSubFolder(Instance instance, String subFolder) {
        try {
            if (instance == null) {
                statusLabel.setText("No hay instancia seleccionada.");
                return;
            }

            File dir = InstanceManager.getGameDir(instance);

            if (subFolder != null && !subFolder.trim().isEmpty()) {
                dir = new File(dir, subFolder);
            }

            dir.mkdirs();

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            statusLabel.setText("No se pudo abrir carpeta: " + ex.getMessage());
        }
    }

    private void showInstanceSwitcherDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Instancias");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Instancias");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label subtitle = new Label("Selecciona, crea o administra tus instancias.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        header.getChildren().addAll(title, subtitle);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");

        VBox content = new VBox(12);
        content.setPadding(new Insets(0, 24, 20, 24));

        if (instances == null || instances.isEmpty()) {
            Label empty = new Label("No hay instancias.");
            empty.setStyle("-fx-text-fill: #6b7280;");
            content.getChildren().add(empty);
        } else {
            for (final Instance instance : instances) {
                VBox card = createInstanceVisualCard(instance, instance == selectedInstance);
                card.setCursor(javafx.scene.Cursor.HAND);

                card.setOnMouseClicked(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent event) {
                        selectInstance(instance);
                        dialog.close();
                    }
                });

                content.getChildren().add(card);
            }
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(0, 24, 24, 24));

        Button newBtn = new Button("Nueva");
        newBtn.getStyleClass().add("button");

        Button editBtn = new Button("Editar");
        editBtn.getStyleClass().add("secondary-button");

        Button duplicateBtn = new Button("Duplicar");
        duplicateBtn.getStyleClass().add("secondary-button");

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("secondary-button");

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("secondary-button");

        newBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
                createNewInstance();
            }
        });

        editBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    Instance instance = getCurrentInstance();

                    if (instance == null) {
                        statusLabel.setText("No hay instancia seleccionada.");
                        showToast("No hay instancia seleccionada", "warning");
                        return;
                    }

                    dialog.close();
                    openEditInstanceSafely();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error abriendo editor: " + ex.getMessage());
                    showToast("Error abriendo editor", "error");
                }
            }
        });

        duplicateBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
                duplicateSelectedInstance();
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
                deleteSelectedInstanceLauncher();
            }
        });

        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        actions.getChildren().addAll(newBtn, editBtn, duplicateBtn, deleteBtn, closeBtn);

        scroll.setContent(content);

        root.setTop(header);
        root.setCenter(scroll);
        root.setBottom(actions);

        Scene scene = new Scene(root, 620, 640);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(560);
        dialog.setMinHeight(520);
        dialog.show();
    }

    private void showEditInstanceDialog() {
        final Instance instance = getCurrentInstance();

        if (instance == null) {
            statusLabel.setText("No hay instancia seleccionada.");
            return;
        }

        final Stage dialog = new Stage();
        dialog.setTitle("Editar instancia");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Editar instancia");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label subtitle = new Label(instance.name == null ? "Instancia" : instance.name);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");

        VBox content = new VBox(16);
        content.setPadding(new Insets(0, 24, 20, 24));

        /*
         * GENERAL
         */
        VBox generalCard = new VBox(12);
        generalCard.getStyleClass().add("namemc-card");

        Label generalTitle = new Label("General");
        generalTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        final TextField nameField = new TextField(instance.name == null ? "" : instance.name);
        nameField.setPromptText("Nombre de la instancia");

        final ComboBox<String> iconEditBox = new ComboBox<String>();
        iconEditBox.getItems().addAll(
                "🌱", "⚔", "✨", "⚙", "🧪", "🎮", "🔥", "💎", "🧱", "🧭", "🚀"
        );
        iconEditBox.setMaxWidth(Double.MAX_VALUE);

        if (instance.icon != null && !instance.icon.trim().isEmpty()) {
            iconEditBox.getSelectionModel().select(getInstanceIcon(instance));
        } else {
            iconEditBox.getSelectionModel().select("🌱");
        }

        final TextField usernameEditField = new TextField(
                instance.username == null || instance.username.trim().isEmpty()
                        ? usernameField.getText()
                        : instance.username
        );
        usernameEditField.setPromptText("Usuario");

        final ComboBox<VersionEntry> versionEditBox = new ComboBox<VersionEntry>();
        versionEditBox.setMaxWidth(Double.MAX_VALUE);

        if (allVersions != null) {
            versionEditBox.getItems().setAll(allVersions);
        } else if (versionBox != null) {
            versionEditBox.getItems().setAll(versionBox.getItems());
        }

        if (instance.version != null && !instance.version.trim().isEmpty()) {
            for (VersionEntry v : versionEditBox.getItems()) {
                if (v != null && v.id != null && v.id.equals(instance.version)) {
                    versionEditBox.getSelectionModel().select(v);
                    break;
                }
            }
        } else if (versionBox.getValue() != null) {
            versionEditBox.getSelectionModel().select(versionBox.getValue());
        }

        final Label typeLabel = new Label("Tipo: " + getInstanceTypeLabel(instance));
        typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        versionEditBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<VersionEntry>() {
            @Override
            public void changed(ObservableValue<? extends VersionEntry> observable, VersionEntry oldValue, VersionEntry newValue) {
                if (newValue != null) {
                    typeLabel.setText("Tipo: " + detectProfileType(newValue.id));
                }
            }
        });

        generalCard.getChildren().addAll(
                generalTitle,
                new Label("Nombre"),
                nameField,
                new Label("Icono"),
                iconEditBox,
                new Label("Usuario"),
                usernameEditField,
                new Label("Versión"),
                versionEditBox,
                typeLabel
        );

        /*
         * CUSTOM CLIENT JAR
         */
        VBox customClientCard = new VBox(12);
        customClientCard.getStyleClass().add("namemc-card");

        Label customClientTitle = new Label("Cliente personalizado");
        customClientTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label customClientInfo = new Label(
                "Puedes usar un .jar personalizado para esta instancia. " +
                        "Debe ser compatible con la versión seleccionada."
        );
        customClientInfo.setWrapText(true);
        customClientInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        final Label customClientPathLabel = new Label(getCustomClientLabel(instance));
        customClientPathLabel.setWrapText(true);
        customClientPathLabel.setStyle(
                "-fx-font-size: 12px;" +
                        "-fx-text-fill: #4b5563;" +
                        "-fx-background-color: #f9fafb;" +
                        "-fx-background-radius: 12px;" +
                        "-fx-border-color: #e5e7eb;" +
                        "-fx-border-radius: 12px;" +
                        "-fx-padding: 10px;"
        );

        HBox customClientButtons = new HBox(8);

        Button chooseCustomClientBtn = new Button("Elegir .jar");
        chooseCustomClientBtn.getStyleClass().add("button");

        Button clearCustomClientBtn = new Button("Quitar");
        clearCustomClientBtn.getStyleClass().add("secondary-button");

        Button openCustomClientFolderBtn = new Button("Abrir carpeta");
        openCustomClientFolderBtn.getStyleClass().add("secondary-button");

        customClientButtons.getChildren().addAll(
                chooseCustomClientBtn,
                clearCustomClientBtn,
                openCustomClientFolderBtn
        );

        chooseCustomClientBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File selectedJar = chooseJarFile(dialog, "Elegir cliente personalizado .jar");

                    if (selectedJar == null) {
                        return;
                    }

                    File copied = importCustomClientJarToInstance(instance, selectedJar);

                    instance.customClientJarPath = copied.getAbsolutePath();
                    InstanceManager.saveInstance(instance);

                    customClientPathLabel.setText(getCustomClientLabel(instance));
                    statusLabel.setText("Cliente personalizado asignado: " + copied.getName());
                    showToast("Cliente personalizado asignado", "success");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error asignando cliente personalizado: " + ex.getMessage());
                    showToast("Error asignando cliente personalizado", "error");
                }
            }
        });

        clearCustomClientBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    instance.customClientJarPath = "";
                    InstanceManager.saveInstance(instance);

                    customClientPathLabel.setText(getCustomClientLabel(instance));
                    statusLabel.setText("Cliente personalizado eliminado.");
                    showToast("Cliente personalizado eliminado", "info");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error eliminando cliente personalizado: " + ex.getMessage());
                }
            }
        });

        openCustomClientFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File dir = new File(InstanceManager.getGameDir(instance), "client");
                    dir.mkdirs();
                    openFolder(dir);
                } catch (Exception ex) {
                    statusLabel.setText("No se pudo abrir carpeta del cliente: " + ex.getMessage());
                }
            }
        });

        customClientCard.getChildren().addAll(
                customClientTitle,
                customClientInfo,
                customClientPathLabel,
                customClientButtons
        );

        /*
         * JAVA / RAM
         */
        VBox javaCard = new VBox(12);
        javaCard.getStyleClass().add("namemc-card");

        Label javaTitle = new Label("Java y memoria");
        javaTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        HBox ramHeader = new HBox(10);
        ramHeader.setAlignment(Pos.CENTER_LEFT);

        Label ramEditTitle = new Label("RAM asignada");
        ramEditTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Region ramSpacer = new Region();
        HBox.setHgrow(ramSpacer, Priority.ALWAYS);

        final Label ramEditValue = new Label((instance.ram <= 0 ? 2 : instance.ram) + " GB");
        ramEditValue.getStyleClass().add("ram-value");

        ramHeader.getChildren().addAll(ramEditTitle, ramSpacer, ramEditValue);

        final Slider ramEditSlider = new Slider(1, 16, instance.ram <= 0 ? 2 : instance.ram);
        ramEditSlider.setShowTickLabels(false);
        ramEditSlider.setShowTickMarks(false);
        ramEditSlider.setMajorTickUnit(1);
        ramEditSlider.setMinorTickCount(0);
        ramEditSlider.setSnapToTicks(true);
        ramEditSlider.getStyleClass().add("ram-slider");

        ramEditSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                ramEditValue.setText(newValue.intValue() + " GB");
            }
        });

        Label javaHint = new Label("Java se selecciona automáticamente según la versión de Minecraft.");
        javaHint.setWrapText(true);
        javaHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label jvmArgsTitle = new Label("Argumentos JVM avanzados");
        jvmArgsTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        final TextArea jvmArgsArea = new TextArea(instance.jvmArgs == null ? "" : instance.jvmArgs);
        jvmArgsArea.setPromptText("-XX:+UseG1GC -XX:MaxGCPauseMillis=50");
        jvmArgsArea.setWrapText(true);
        jvmArgsArea.setPrefHeight(80);
        jvmArgsArea.getStyleClass().add("console-output");

        Label jvmArgsHint = new Label("Opcional. Déjalo vacío si no sabes qué poner.");
        jvmArgsHint.setWrapText(true);
        jvmArgsHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        javaCard.getChildren().addAll(
                javaTitle,
                ramHeader,
                ramEditSlider,
                javaHint,
                jvmArgsTitle,
                jvmArgsArea,
                jvmArgsHint
        );

        /*
         * NOTES
         */
        VBox notesCard = new VBox(12);
        notesCard.getStyleClass().add("namemc-card");

        Label notesTitle = new Label("Notas");
        notesTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        final TextArea notesArea = new TextArea(instance.notes == null ? "" : instance.notes);
        notesArea.setPromptText("Notas de la instancia...");
        notesArea.setWrapText(true);
        notesArea.setPrefHeight(100);

        notesCard.getChildren().addAll(notesTitle, notesArea);

        /*
         * FOLDERS
         */
        VBox foldersCard = new VBox(12);
        foldersCard.getStyleClass().add("namemc-card");

        Label foldersTitle = new Label("Carpetas");
        foldersTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        File gameDir = InstanceManager.getGameDir(instance);

        Label pathLabel = new Label(gameDir.getAbsolutePath());
        pathLabel.setWrapText(true);
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        HBox folderRow1 = new HBox(8);
        HBox folderRow2 = new HBox(8);

        Button openRootBtn = new Button("Principal");
        openRootBtn.getStyleClass().add("secondary-button");
        openRootBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openRootBtn, Priority.ALWAYS);

        Button openModsBtn = new Button("Mods");
        openModsBtn.getStyleClass().add("secondary-button");
        openModsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openModsBtn, Priority.ALWAYS);

        Button openResourcepacksBtn = new Button("Texturas");
        openResourcepacksBtn.getStyleClass().add("secondary-button");
        openResourcepacksBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openResourcepacksBtn, Priority.ALWAYS);

        Button openShaderpacksBtn = new Button("Shaders");
        openShaderpacksBtn.getStyleClass().add("secondary-button");
        openShaderpacksBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openShaderpacksBtn, Priority.ALWAYS);

        Button openConfigBtn = new Button("Config");
        openConfigBtn.getStyleClass().add("secondary-button");
        openConfigBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openConfigBtn, Priority.ALWAYS);

        Button openLogsBtn = new Button("Logs");
        openLogsBtn.getStyleClass().add("secondary-button");
        openLogsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(openLogsBtn, Priority.ALWAYS);

        folderRow1.getChildren().addAll(openRootBtn, openModsBtn, openResourcepacksBtn);
        folderRow2.getChildren().addAll(openShaderpacksBtn, openConfigBtn);

        openRootBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "");
            }
        });

        folderRow2.getChildren().addAll(openShaderpacksBtn, openConfigBtn, openLogsBtn);

        openLogsBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "logs");
            }
        });

        openModsBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "mods");
            }
        });

        openResourcepacksBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "resourcepacks");
            }
        });

        openShaderpacksBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "shaderpacks");
            }
        });

        openConfigBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "config");
            }
        });

        foldersCard.getChildren().addAll(foldersTitle, pathLabel, folderRow1, folderRow2);

        /*
         * FOOTER
         */
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("secondary-button");

        Button saveBtn = new Button("Guardar cambios");
        saveBtn.getStyleClass().add("button");

        footer.getChildren().addAll(cancelBtn, saveBtn);

        cancelBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        saveBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String newName = nameField.getText() == null ? "" : nameField.getText().trim();

                if (newName.isEmpty()) {
                    statusLabel.setText("El nombre de la instancia no puede estar vacío.");
                    return;
                }

                VersionEntry selectedVersion = versionEditBox.getValue();

                if (selectedVersion == null) {
                    statusLabel.setText("Selecciona una versión para la instancia.");
                    return;
                }

                try {
                    instance.name = newName;
                    instance.icon = iconEditBox.getValue() == null ? "🌱" : iconEditBox.getValue();
                    instance.username = usernameEditField.getText() == null ? "" : usernameEditField.getText().trim();
                    instance.version = selectedVersion.id;
                    instance.type = detectProfileType(selectedVersion.id);
                    instance.ram = (int) ramEditSlider.getValue();
                    instance.notes = notesArea.getText() == null ? "" : notesArea.getText();
                    instance.jvmArgs = jvmArgsArea.getText() == null ? "" : jvmArgsArea.getText().trim();

                    InstanceManager.ensureInstanceFolders(instance);
                    InstanceManager.saveInstance(instance);

                    usernameField.setText(instance.username);
                    ramSlider.setValue(instance.ram);
                    selectVersionById(instance.version);

                    refreshInstanceViews();
                    selectInstance(instance);

                    prefs.put("lastInstanceName", instance.name);

                    statusLabel.setText("Instancia guardada: " + instance.name);

                    dialog.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error guardando instancia: " + ex.getMessage());
                }
            }
        });

        content.getChildren().addAll(generalCard, customClientCard, javaCard, notesCard, foldersCard, footer);

        scroll.setContent(content);

        root.setTop(header);
        root.setCenter(scroll);
        Scene scene = new Scene(root, 1100, 680);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(620);
        dialog.setMinHeight(600);
        dialog.show();
    }
    private void loadInstancesIntoCombo() {
        System.out.println("[Instances] Cargando instancias...");

        instances = InstanceManager.loadInstances();

        if (instances == null) {
            instances = new ArrayList<Instance>();
        }

        if (instances.isEmpty()) {
            try {
                System.out.println("[Instances] No hay instancias, creando Principal...");
                Instance def = InstanceManager.createDefaultInstance();
                instances.add(def);
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Error creando instancia principal: " + ex.getMessage());
            }
        }

        System.out.println("[Instances] Instancias cargadas: " + instances.size());

        refreshInstanceViews();

        String lastInstanceName = prefs.get("lastInstanceName", "");

        if (!lastInstanceName.isEmpty()) {
            for (Instance instance : instances) {
                if (instance.name != null && instance.name.equals(lastInstanceName)) {
                    selectInstance(instance);
                    return;
                }
            }
        }

        if (!instances.isEmpty()) {
            selectInstance(instances.get(0));
        }
    }

    private void selectInstance(Instance instance) {
        if (instance == null) {
            return;
        }

        System.out.println("[Instances] Seleccionando instancia: " + instance.name);

        selectedInstance = instance;

        if (instanceBox != null) {
            instanceBox.getSelectionModel().select(instance);
        }

        renderInstanceCards();
        applyInstance(instance);
    }

    private void showInstanceTemplateDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Nueva instancia");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Nueva instancia");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label subtitle = new Label("Elige una plantilla para crear tu instancia.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(12);
        content.setPadding(new Insets(0, 24, 20, 24));

        VBox vanilla = createTemplateCard(
                "Vanilla",
                "Minecraft limpio sin mods. Ideal para jugar sin modificaciones.",
                "🌱"
        );

        VBox fabricPerformance = createTemplateCard(
                "Fabric rendimiento",
                "Fabric con mods para mejorar FPS y estabilidad.",
                "⚡"
        );

        VBox fabricShaders = createTemplateCard(
                "Fabric shaders",
                "Fabric con Sodium, Iris y shaderpack recomendado.",
                "✨"
        );

        VBox pvp189 = createTemplateCard(
                "PvP 1.8.9",
                "Instancia para PvP clásico en Minecraft 1.8.9.",
                "⚔"
        );

        VBox custom = createTemplateCard(
                "Vacía personalizada",
                "Crea una instancia eligiendo manualmente la versión actual.",
                "🧩"
        );

        content.getChildren().addAll(vanilla, fabricPerformance, fabricShaders, pvp189, custom);

        vanilla.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                dialog.close();
                createInstanceFromTemplate("vanilla");
            }
        });

        fabricPerformance.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                dialog.close();
                createInstanceFromTemplate("fabric-performance");
            }
        });

        fabricShaders.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                dialog.close();
                createInstanceFromTemplate("fabric-shaders");
            }
        });

        pvp189.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                dialog.close();
                createInstanceFromTemplate("pvp-1.8.9");
            }
        });

        custom.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                dialog.close();
                createInstanceFromTemplate("custom");
            }
        });

        root.setTop(header);
        root.setCenter(content);

        Scene scene = new Scene(root, 560, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(520);
        dialog.setMinHeight(560);
        dialog.show();
    }

    private VBox createTemplateCard(String titleText, String descriptionText, String iconText) {
        VBox card = new VBox(8);
        card.getStyleClass().add("template-card");
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setPadding(new Insets(14));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(iconText);
        icon.getStyleClass().add("template-card-icon");

        Label title = new Label(titleText);
        title.getStyleClass().add("template-card-title");

        top.getChildren().addAll(icon, title);

        Label description = new Label(descriptionText);
        description.getStyleClass().add("template-card-description");
        description.setWrapText(true);

        card.getChildren().addAll(top, description);

        return card;
    }

    private void createInstanceFromTemplate(final String templateId) {
        TextInputDialog nameDialog = new TextInputDialog(getDefaultTemplateName(templateId));
        nameDialog.setTitle("Nueva instancia");
        nameDialog.setHeaderText("Nombre de la instancia");
        nameDialog.setContentText("Nombre:");

        java.util.Optional<String> result = nameDialog.showAndWait();

        if (!result.isPresent()) {
            return;
        }

        final String name = result.get() == null ? "" : result.get().trim();

        if (name.isEmpty()) {
            statusLabel.setText("El nombre de la instancia no puede estar vacío.");
            return;
        }

        final String username = usernameField.getText() == null || usernameField.getText().trim().isEmpty()
                ? "Steve"
                : usernameField.getText().trim();

        final TemplateConfig config = getTemplateConfig(templateId);

        statusLabel.setText("Creando instancia " + name + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Instance> task = new Task<Instance>() {
            @Override
            protected Instance call() throws Exception {
                Instance instance = InstanceManager.createInstance(
                        name,
                        username,
                        config.version,
                        config.type,
                        config.ram
                );

                if (config.installFabric) {
                    final String mcVersion = config.version;

                    FabricManager.install(
                            new VersionEntry(mcVersion),
                            new FabricManager.LauncherCallback() {
                                @Override
                                public void onStatus(String status) {
                                    System.out.println("[Template] " + status);
                                }

                                @Override
                                public void onSuccess(String installedVersionId) {
                                    instance.version = installedVersionId;
                                    instance.type = "fabric";

                                    try {
                                        InstanceManager.saveInstance(instance);
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }

                                @Override
                                public void onError(String error) {
                                    System.err.println("[Template] Error instalando Fabric: " + error);
                                }
                            }
                    );

                    /*
                     * FabricManager instala en otro thread.
                     * Esperamos un poco para que el perfil se cree.
                     */
                    waitForFabricProfile(instance, mcVersion, 30000);
                }

                InstanceManager.ensureInstanceFolders(instance);

                if (config.mods != null && config.mods.length > 0) {
                    File modsDir = new File(InstanceManager.getGameDir(instance), "mods");

                    for (String slug : config.mods) {
                        try {
                            ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                                    slug,
                                    extractMinecraftVersionForMods(instance.version),
                                    "mod"
                            );

                            File target = new File(modsDir, safeModFileName(fileData.filename));

                            if (!target.exists()) {
                                downloadModFile(fileData.url, target);
                            }
                        } catch (Exception ex) {
                            System.err.println("[Template] No se pudo instalar mod " + slug + ": " + ex.getMessage());
                        }
                    }
                }

                if (config.shaderSlug != null && !config.shaderSlug.trim().isEmpty()) {
                    try {
                        File shaderDir = new File(InstanceManager.getGameDir(instance), "shaderpacks");

                        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                                config.shaderSlug,
                                extractMinecraftVersionForMods(instance.version),
                                "shader"
                        );

                        File target = new File(shaderDir, safeModFileName(fileData.filename));

                        if (!target.exists()) {
                            downloadModFile(fileData.url, target);
                        }
                    } catch (Exception ex) {
                        System.err.println("[Template] No se pudo instalar shader " + config.shaderSlug + ": " + ex.getMessage());
                    }
                }

                InstanceManager.saveInstance(instance);

                return instance;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Instance instance = task.getValue();
                instances.add(instance);
                refreshInstanceViews();
                selectInstance(instance);

                prefs.put("lastInstanceName", instance.name);

                loadVersions();

                statusLabel.setText("✅ Instancia creada: " + instance.name);
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                if (ex != null) {
                    statusLabel.setText("❌ Error creando instancia: " + ex.getMessage());
                    ex.printStackTrace();
                } else {
                    statusLabel.setText("❌ Error creando instancia.");
                }
            }
        });

        Thread t = new Thread(task, "Create-Instance-Template");
        t.setDaemon(true);
        t.start();
    }

    private static class TemplateConfig {
        String version;
        String type;
        int ram;
        boolean installFabric;
        String[] mods;
        String shaderSlug;

        TemplateConfig(String version, String type, int ram, boolean installFabric, String[] mods, String shaderSlug) {
            this.version = version;
            this.type = type;
            this.ram = ram;
            this.installFabric = installFabric;
            this.mods = mods;
            this.shaderSlug = shaderSlug;
        }
    }

    private TemplateConfig getTemplateConfig(String templateId) {
        if ("fabric-performance".equals(templateId)) {
            return new TemplateConfig(
                    getRecommendedModernVersion(),
                    "fabric",
                    4,
                    true,
                    new String[]{
                            "sodium",
                            "lithium",
                            "ferrite-core",
                            "modmenu",
                            "fabric-api"
                    },
                    null
            );
        }

        if ("fabric-shaders".equals(templateId)) {
            return new TemplateConfig(
                    getRecommendedModernVersion(),
                    "fabric",
                    6,
                    true,
                    new String[]{
                            "sodium",
                            "iris",
                            "sodium-extra",
                            "reeses-sodium-options",
                            "indium",
                            "fabric-api"
                    },
                    "complementary-reimagined"
            );
        }

        if ("pvp-1.8.9".equals(templateId)) {
            return new TemplateConfig(
                    "1.8.9",
                    "vanilla",
                    2,
                    false,
                    new String[]{},
                    null
            );
        }

        if ("custom".equals(templateId)) {
            String version = versionBox.getValue() == null ? getRecommendedModernVersion() : versionBox.getValue().id;

            return new TemplateConfig(
                    version,
                    detectProfileType(version),
                    (int) ramSlider.getValue(),
                    false,
                    new String[]{},
                    null
            );
        }

        return new TemplateConfig(
                getRecommendedModernVersion(),
                "vanilla",
                2,
                false,
                new String[]{},
                null
        );
    }

    private String getDefaultTemplateName(String templateId) {
        if ("fabric-performance".equals(templateId)) {
            return "Fabric rendimiento";
        }

        if ("fabric-shaders".equals(templateId)) {
            return "Fabric shaders";
        }

        if ("pvp-1.8.9".equals(templateId)) {
            return "PvP 1.8.9";
        }

        if ("custom".equals(templateId)) {
            return "Instancia personalizada";
        }

        return "Vanilla";
    }

    private String getRecommendedModernVersion() {
        if (allVersions != null && !allVersions.isEmpty()) {
            for (VersionEntry v : allVersions) {
                if (v != null && v.id != null && v.id.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                    return v.id;
                }
            }
        }

        if (versionBox != null && versionBox.getValue() != null) {
            return versionBox.getValue().id;
        }

        return "1.21.1";
    }

    private void waitForFabricProfile(Instance instance, String mcVersion, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMs) {
            File versionsDir = new File(VersionManager.MC_DIR, "versions");
            File[] dirs = versionsDir.listFiles();

            if (dirs != null) {
                File newest = null;

                for (File dir : dirs) {
                    String name = dir.getName();

                    if (name.toLowerCase().contains("fabric")
                            && name.endsWith("-" + mcVersion)) {
                        File json = new File(dir, name + ".json");

                        if (json.exists()) {
                            if (newest == null || json.lastModified() > new File(newest, newest.getName() + ".json").lastModified()) {
                                newest = dir;
                            }
                        }
                    }
                }

                if (newest != null) {
                    instance.version = newest.getName();
                    instance.type = "fabric";
                    InstanceManager.saveInstance(instance);
                    return;
                }
            }

            Thread.sleep(500);
        }

        throw new Exception("Fabric tardó demasiado en instalarse.");
    }

    private void createNewInstance() {    showInstanceTemplateDialog();}

    private String getInstanceIcon(Instance instance) {
        if (instance == null || instance.icon == null || instance.icon.trim().isEmpty()) {
            return "🌱";
        }

        String icon = instance.icon.trim();

        if (isKnownInstanceEmoji(icon)) {
            return icon;
        }

        String lower = icon.toLowerCase();

        if (lower.contains("sword") || lower.contains("pvp")) {
            return "⚔";
        }

        if (lower.contains("shader") || lower.contains("star")) {
            return "✨";
        }

        if (lower.contains("tech") || lower.contains("gear")) {
            return "⚙";
        }

        if (lower.contains("test") || lower.contains("lab")) {
            return "🧪";
        }

        if (lower.contains("fire")) {
            return "🔥";
        }

        if (lower.contains("diamond")) {
            return "💎";
        }

        if (lower.contains("brick")) {
            return "🧱";
        }

        if (lower.contains("grass")) {
            return "🌱";
        }

        return "🎮";
    }

    private boolean isKnownInstanceEmoji(String icon) {
        return "🌱".equals(icon)
                || "⚔".equals(icon)
                || "✨".equals(icon)
                || "⚙".equals(icon)
                || "🧪".equals(icon)
                || "🎮".equals(icon)
                || "🔥".equals(icon)
                || "💎".equals(icon)
                || "🧱".equals(icon)
                || "🧭".equals(icon)
                || "🚀".equals(icon);
    }

    private String getInstanceTypeLabel(Instance instance) {
        if (instance == null || instance.type == null || instance.type.trim().isEmpty()) {
            return "Vanilla";
        }

        String type = instance.type.trim().toLowerCase();

        if (type.contains("fabric")) {
            return "Fabric";
        }

        if (type.contains("forge")) {
            return "Forge";
        }

        if (type.contains("quilt")) {
            return "Quilt";
        }

        if (type.contains("optifine")) {
            return "OptiFine";
        }

        return "Vanilla";
    }

    private String getInstanceMetaText(Instance instance) {
        if (instance == null) {
            return "Sin versión · 2 GB";
        }

        String version = instance.version == null || instance.version.trim().isEmpty()
                ? "Sin versión"
                : instance.version;

        int ram = instance.ram <= 0 ? 2 : instance.ram;

        boolean customClient = instance.customClientJarPath != null
                && !instance.customClientJarPath.trim().isEmpty()
                && new File(instance.customClientJarPath).exists();

        if (customClient) {
            return version + " · " + ram + " GB RAM · Cliente custom";
        }

        return version + " · " + ram + " GB RAM";
    }

    private String getInstanceModsSummary(Instance instance) {
        if (instance == null) {
            return "0 mods";
        }

        File modsDir = new File(InstanceManager.getGameDir(instance), "mods");

        if (!modsDir.exists()) {
            return "0 mods";
        }

        File[] files = modsDir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();
                return lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".disabled");
            }
        });

        if (files == null || files.length == 0) {
            return "0 mods";
        }

        int active = 0;
        int disabled = 0;

        for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".jar")) {
                active++;
            } else {
                disabled++;
            }
        }

        if (disabled > 0) {
            return active + " activos · " + disabled + " desactivados";
        }

        return active + " mods activos";
    }

    private void saveSelectedInstance() {
        Instance instance = getCurrentInstance();

        if (instance == null) {
            statusLabel.setText("No hay instancia seleccionada para guardar.");
            return;
        }

        try {
            instance.username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            instance.ram = (int) ramSlider.getValue();

            if (versionBox != null && versionBox.getValue() != null) {
                instance.version = versionBox.getValue().id;
                instance.type = detectProfileType(instance.version);
            }

            if (instance.name == null || instance.name.trim().isEmpty()) {
                instance.name = "Instancia";
            }

            InstanceManager.ensureInstanceFolders(instance);
            InstanceManager.saveInstance(instance);

            prefs.put("lastInstanceName", instance.name);

            refreshInstanceViews();
            selectInstance(instance);

            statusLabel.setText("✅ Instancia guardada: " + instance.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("❌ Error guardando instancia: " + ex.getMessage());
        }
    }

    private void duplicateSelectedInstance() {
        final Instance selected = getCurrentInstance();

        if (selected == null) {
            statusLabel.setText("No hay instancia seleccionada para duplicar.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.name + " copia");
        dialog.setTitle("Duplicar instancia");
        dialog.setHeaderText("Nombre de la copia");
        dialog.setContentText("Nombre:");

        java.util.Optional<String> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return;
        }

        String name = result.get() == null ? "" : result.get().trim();

        if (name.isEmpty()) {
            statusLabel.setText("El nombre no puede estar vacío.");
            return;
        }

        try {
            Instance copy = InstanceManager.duplicateInstance(selected, name);

            instances.add(copy);

            refreshInstanceViews();
            selectInstance(copy);

            prefs.put("lastInstanceName", copy.name);

            statusLabel.setText("✅ Instancia duplicada: " + copy.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("❌ Error duplicando instancia: " + ex.getMessage());
        }
    }

    private void deleteSelectedInstanceLauncher() {
        final Instance selected = getCurrentInstance();

        if (selected == null) {
            statusLabel.setText("No hay instancia seleccionada para eliminar.");
            return;
        }

        if (instances.size() <= 1) {
            statusLabel.setText("No puedes eliminar la única instancia.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar instancia");
        confirm.setHeaderText("¿Eliminar esta instancia?");
        confirm.setContentText(selected.name + "\n\nSe eliminarán sus mods, configs, resourcepacks y shaderpacks.");

        java.util.Optional<ButtonType> result = confirm.showAndWait();

        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            InstanceManager.deleteInstance(selected);

            instances.remove(selected);

            refreshInstanceViews();

            if (!instances.isEmpty()) {
                Instance next = instances.get(0);
                selectInstance(next);
                prefs.put("lastInstanceName", next.name);
            }

            statusLabel.setText("✅ Instancia eliminada: " + selected.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("❌ Error eliminando instancia: " + ex.getMessage());
        }
    }

    private void applyInstance(Instance instance) {
        if (instance == null) {
            return;
        }

        if (instance.username != null && !instance.username.trim().isEmpty()) {
            usernameField.setText(instance.username);
        }

        if (instance.ram > 0) {
            ramSlider.setValue(instance.ram);
        }

        if (instance.version != null && !instance.version.trim().isEmpty()) {
            selectVersionById(instance.version);
            prefs.put("lastVersion", instance.version);
        }

        prefs.put("lastInstanceName", instance.name == null ? "" : instance.name);

        InstanceManager.ensureInstanceFolders(instance);

        updateHeroInstance(instance);

        statusLabel.setText("Instancia aplicada: " + instance.name);
    }

    private void updateHeroInstance(Instance instance) {
        if (instance == null) {
            return;
        }

        if (heroInstanceIconLabel != null) {
            heroInstanceIconLabel.setText(getInstanceIcon(instance));
        }

        if (heroInstanceNameLabel != null) {
            heroInstanceNameLabel.setText(instance.name == null ? "Instancia" : instance.name);
        }

        if (heroInstanceMetaLabel != null) {
            heroInstanceMetaLabel.setText(getInstanceMetaText(instance));
        }

        if (heroInstanceModsLabel != null) {
            heroInstanceModsLabel.setText(getInstanceModsSummary(instance));
        }

        if (heroUserLabel != null) {
            String user = usernameField == null || usernameField.getText() == null || usernameField.getText().trim().isEmpty()
                    ? "Steve"
                    : usernameField.getText().trim();

            heroUserLabel.setText("Usuario: " + user);
        }

        if (topInstanceBtn != null) {
            topInstanceBtn.setText(instance.name == null ? "Instancia" : instance.name);
        }

        updateDashboardStats(instance);

    }

    private Instance getCurrentInstance() {
        if (selectedInstance != null) {
            return selectedInstance;
        }

        if (instanceBox != null && instanceBox.getValue() != null) {
            return instanceBox.getValue();
        }

        if (instances != null && !instances.isEmpty()) {
            selectedInstance = instances.get(0);
            return selectedInstance;
        }

        return null;
    }

    private void refreshInstanceViews() {
        System.out.println("[Instances] Refrescando vistas...");

        if (instanceBox != null) {
            instanceBox.getItems().setAll(instances);
        }

        renderInstanceCards();
    }

    private void renderInstanceCards() {
        if (instanceCardsBox == null) {
            System.out.println("[Instances] instanceCardsBox es NULL");
            return;
        }

        instanceCardsBox.getChildren().clear();

        if (instances == null || instances.isEmpty()) {
            Label empty = new Label("No hay instancias.");
            empty.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
            instanceCardsBox.getChildren().add(empty);
            return;
        }

        for (final Instance instance : instances) {
            VBox card = createInstanceVisualCard(instance, instance == selectedInstance);

            card.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    selectInstance(instance);
                }
            });

            instanceCardsBox.getChildren().add(card);
        }
    }

    private VBox createInstanceVisualCard(Instance item, boolean selected) {
        VBox wrapper = new VBox();
        wrapper.setMaxWidth(Double.MAX_VALUE);
        wrapper.getStyleClass().add(selected ? "instance-card-selected-wrapper" : "instance-card-wrapper");

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12));
        card.setMinHeight(78);
        card.setPrefHeight(78);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add(selected ? "instance-card-selected" : "instance-card");

        Label icon = new Label(getInstanceIcon(item));
        icon.getStyleClass().add("instance-card-icon");

        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(item.name == null ? "Instancia" : item.name);
        name.getStyleClass().add("instance-card-title");
        name.setMaxWidth(170);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label typeBadge = new Label(getInstanceTypeLabel(item));
        typeBadge.getStyleClass().add("instance-type-badge");

        top.getChildren().addAll(name, topSpacer, typeBadge);

        Label meta = new Label(getInstanceMetaText(item));
        meta.getStyleClass().add("instance-card-meta");
        meta.setWrapText(true);

        Label mods = new Label(getInstanceModsSummary(item));
        mods.getStyleClass().add("instance-card-submeta");

        info.getChildren().addAll(top, meta, mods);
        card.getChildren().addAll(icon, info);

        wrapper.getChildren().add(card);

        return wrapper;
    }

    private File getCurrentInstanceGameDir() {
        Instance instance = getCurrentInstance();

        if (instance == null) {
            return VersionManager.MC_DIR;
        }

        InstanceManager.ensureInstanceFolders(instance);
        return InstanceManager.getGameDir(instance);
    }

    private String getContentFallbackIcon(String type) {
        if ("resourcepack".equalsIgnoreCase(type)) {
            return "🖼";
        }

        if ("shader".equalsIgnoreCase(type)) {
            return "✨";
        }

        return "📦";
    }

    private void loadPopularContent(final ComboBox<String> typeBox,
                                    final ListView<ModrinthClient.ModResult> resultsList,
                                    final Button searchBtn,
                                    final Button installBtn,
                                    final Label status) {
        final String type = getSelectedContentType(typeBox);

        resultsList.getItems().clear();
        installBtn.setText("Instalar seleccionado");
        installBtn.setDisable(true);
        searchBtn.setDisable(true);

        status.setText("Cargando " + getContentTypeLabelPlural(type).toLowerCase() + " populares...");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ModrinthClient.ModResult> results = ModrinthClient.searchPopularProjects(type);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            resultsList.getItems().setAll(results);
                            searchBtn.setDisable(false);

                            if (results.isEmpty()) {
                                status.setText("No se encontraron contenidos populares.");
                            } else {
                                status.setText("Mostrando " + results.size() + " " + getContentTypeLabelPlural(type).toLowerCase() + " populares.");
                            }
                        }
                    });
                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            searchBtn.setDisable(false);
                            status.setText("Error cargando populares: " + ex.getMessage());
                        }
                    });
                }
            }
        }, "Modrinth-Popular-Content");

        t.setDaemon(true);
        t.start();
    }

    private String getContentTypeLabelPlural(String type) {
        if ("resourcepack".equalsIgnoreCase(type)) {
            return "Texturas";
        }

        if ("shader".equalsIgnoreCase(type)) {
            return "Shaders";
        }

        if ("modpack".equalsIgnoreCase(type)) {
            return "Modpacks";
        }

        return "Mods";
    }

    private static class InstalledModInfo {
        File file;
        String name;
        String description;
        String modId;
        String version;
        boolean enabled;
        Image icon;
        String provider;

        InstalledModInfo(File file) {
            this.file = file;
            this.name = file.getName();
            this.description = "Sin descripción disponible.";
            this.modId = "";
            this.version = "";
            this.enabled = file.getName().toLowerCase().endsWith(".jar");
            this.icon = null;
            this.provider = "Local";
        }
    }

    private void refreshInstalledModsPanel(ListView<InstalledModInfo> installedList, Label statusLabel) {
        File modsDir = getModsDirectory();

        if (!modsDir.exists()) {
            modsDir.mkdirs();
        }

        installedList.getItems().clear();

        File[] files = modsDir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();
                return lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".disabled");
            }
        });

        if (files == null || files.length == 0) {
            statusLabel.setText("No hay mods instalados.");
            return;
        }

        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        int active = 0;
        int disabled = 0;

        for (File file : files) {
            InstalledModInfo info = readInstalledModInfo(file);

            if (info.enabled) {
                active++;
            } else {
                disabled++;
            }

            installedList.getItems().add(info);
        }

        statusLabel.setText("Mods: " + files.length + " · Activos: " + active + " · Desactivados: " + disabled);
    }

    private File installContentFromModrinthWithDependencies(ModrinthClient.ModResult selected,
                                                            String mcVersion,
                                                            Label statusLabel,
                                                            ProgressBar progressBar) throws Exception {
        java.util.Set<String> installing = new java.util.HashSet<String>();
        return installContentRecursive(selected, mcVersion, statusLabel, progressBar, installing, 0);
    }

    private File installContentRecursive(ModrinthClient.ModResult selected,
                                         String mcVersion,
                                         Label statusLabel,
                                         ProgressBar progressBar,
                                         java.util.Set<String> installing,
                                         int depth) throws Exception {
        if (selected == null) {
            throw new Exception("No hay contenido seleccionado.");
        }

        if (depth > 10) {
            throw new Exception("Demasiadas dependencias anidadas.");
        }

        String type = selected.projectType == null || selected.projectType.trim().isEmpty()
                ? "mod"
                : selected.projectType;

        String projectId = selected.projectId;

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new Exception("Project ID inválido.");
        }

        if (isContentInstalled(projectId, type)) {
            File existing = findInstalledContentFile(projectId, type);

            if (existing != null && existing.exists()) {
                return existing;
            }
        }

        if (installing.contains(projectId)) {
            throw new Exception("Dependencia circular detectada: " + projectId);
        }

        installing.add(projectId);

        final String statusText = "Resolviendo " + selected.title + "...";
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(statusText);
            }
        });

        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                projectId,
                mcVersion,
                type
        );

        if (fileData.dependencyProjectIds != null && !fileData.dependencyProjectIds.isEmpty()) {
            for (String depProjectId : fileData.dependencyProjectIds) {
                if (depProjectId == null || depProjectId.trim().isEmpty()) {
                    continue;
                }

                if (isContentInstalled(depProjectId, "mod")) {
                    continue;
                }

                ModrinthClient.ModResult depResult = new ModrinthClient.ModResult(
                        depProjectId,
                        depProjectId,
                        "Dependencia requerida",
                        depProjectId,
                        "mod"
                );

                final String depStatus = "Instalando dependencia requerida: " + depProjectId;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(depStatus);
                    }
                });

                installContentRecursive(depResult, mcVersion, statusLabel, progressBar, installing, depth + 1);
            }
        }

        final String installStatus = "Descargando " + selected.title + "...";
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(installStatus);
            }
        });

        File targetDir = getContentDirectory(type);
        targetDir.mkdirs();

        String safeName = safeModFileName(fileData.filename);
        File targetFile = new File(targetDir, safeName);

        if (targetFile.exists() && targetFile.length() > 0) {
            markContentInstalled(projectId, type, selected.title, targetFile);
            markLogicalContentInstalled(selected, targetFile);
            installing.remove(projectId);
            return targetFile;
        }

        downloadFileWithProgress(
                fileData.url,
                targetFile,
                statusLabel,
                progressBar,
                selected.title
        );

        markContentInstalled(projectId, type, selected.title, targetFile);
        markLogicalContentInstalled(selected, targetFile);

        installing.remove(projectId);

        return targetFile;
    }

    private InstalledModInfo readInstalledModInfo(File file) {
        InstalledModInfo info = new InstalledModInfo(file);
        info.provider = detectInstalledContentProvider(file);

        ZipFile zip = null;

        try {
            zip = new ZipFile(file);

            ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");

            if (fabricEntry != null) {
                java.io.InputStream in = zip.getInputStream(fabricEntry);

                try {
                    JsonObject json = JsonParser.parseReader(new java.io.InputStreamReader(in, "UTF-8")).getAsJsonObject();

                    if (json.has("id") && !json.get("id").isJsonNull()) {
                        info.modId = json.get("id").getAsString();
                    }

                    if (json.has("name") && !json.get("name").isJsonNull()) {
                        info.name = json.get("name").getAsString();
                    }

                    if (json.has("version") && !json.get("version").isJsonNull()) {
                        info.version = json.get("version").getAsString();
                    }

                    if (json.has("description") && !json.get("description").isJsonNull()) {
                        info.description = json.get("description").getAsString();
                    }

                    String iconPath = extractFabricIconPath(json);

                    if (iconPath != null && !iconPath.trim().isEmpty()) {
                        Image icon = readIconFromJar(zip, iconPath);

                        if (icon != null && !icon.isError()) {
                            info.icon = icon;
                        }
                    }

                    if (info.version != null && !info.version.trim().isEmpty()) {
                        info.name = info.name + " " + info.version;
                    }
                } finally {
                    in.close();
                }
            } else {
                info.name = cleanModFileName(file.getName());
                info.description = "No se encontró metadata Fabric en este archivo.";
            }
        } catch (Exception ex) {
            info.name = cleanModFileName(file.getName());
            info.description = "No se pudo leer información del mod.";
        } finally {
            try {
                if (zip != null) {
                    zip.close();
                }
            } catch (Exception ignored) {
            }
        }

        return info;
    }

    private String extractFabricIconPath(JsonObject json) {
        try {
            if (!json.has("icon") || json.get("icon").isJsonNull()) {
                return null;
            }

            JsonElement iconElement = json.get("icon");

            if (iconElement.isJsonPrimitive()) {
                return iconElement.getAsString();
            }

            if (iconElement.isJsonObject()) {
                JsonObject iconObj = iconElement.getAsJsonObject();

                if (iconObj.has("64")) {
                    return iconObj.get("64").getAsString();
                }

                if (iconObj.has("128")) {
                    return iconObj.get("128").getAsString();
                }

                for (java.util.Map.Entry<String, JsonElement> entry : iconObj.entrySet()) {
                    return entry.getValue().getAsString();
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String detectInstalledContentProvider(File file) {
        if (file == null) {
            return "Local";
        }

        File registry = getContentRegistryDirectory();

        if (!registry.exists()) {
            return "Local";
        }

        File[] markers = registry.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".txt");
            }
        });

        if (markers == null) {
            return "Local";
        }

        String filePath;

        try {
            filePath = file.getCanonicalPath();
        } catch (Exception ex) {
            filePath = file.getAbsolutePath();
        }

        for (File marker : markers) {
            java.io.BufferedReader reader = null;

            try {
                reader = new java.io.BufferedReader(new java.io.FileReader(marker));

                String line;
                boolean fileMatches = false;
                String projectId = "";

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("file=")) {
                        String markerPath = line.substring("file=".length()).trim();

                        try {
                            markerPath = new File(markerPath).getCanonicalPath();
                        } catch (Exception ignored) {
                        }

                        if (markerPath.equals(filePath)) {
                            fileMatches = true;
                        }
                    }

                    if (line.startsWith("projectId=")) {
                        projectId = line.substring("projectId=".length()).trim();
                    }
                }

                if (fileMatches) {
                    if (projectId.toLowerCase().startsWith("curseforge:")) {
                        return "CurseForge";
                    }

                    if (!projectId.trim().isEmpty()) {
                        return "Modrinth";
                    }

                    return "Local";
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return "Local";
    }

    private Image readIconFromJar(ZipFile zip, String iconPath) {
        try {
            String normalized = iconPath.startsWith("/") ? iconPath.substring(1) : iconPath;

            ZipEntry iconEntry = zip.getEntry(normalized);

            if (iconEntry == null) {
                return null;
            }

            java.io.InputStream in = zip.getInputStream(iconEntry);

            try {
                return new Image(in, 50, 50, true, true);
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private File toggleModEnabled(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("El archivo no existe.");
        }

        String name = file.getName();
        File target;

        if (name.toLowerCase().endsWith(".jar")) {
            target = new File(file.getParentFile(), name + ".disabled");
        } else if (name.toLowerCase().endsWith(".jar.disabled")) {
            target = new File(file.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        } else if (name.toLowerCase().endsWith(".disabled")) {
            target = new File(file.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        } else {
            throw new Exception("Extensión no soportada.");
        }

        if (target.exists()) {
            throw new Exception("Ya existe un archivo con el nombre destino: " + target.getName());
        }

        boolean renamed = file.renameTo(target);

        if (!renamed) {
            throw new Exception("No se pudo renombrar el archivo.");
        }

        return target;
    }

    private String cleanModFileName(String fileName) {
        if (fileName == null) {
            return "Mod desconocido";
        }

        String name = fileName;

        if (name.toLowerCase().endsWith(".jar.disabled")) {
            name = name.substring(0, name.length() - ".jar.disabled".length());
        } else if (name.toLowerCase().endsWith(".disabled")) {
            name = name.substring(0, name.length() - ".disabled".length());
        } else if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - ".jar".length());
        }

        name = name.replace("-", " ").replace("_", " ").trim();

        if (name.isEmpty()) {
            return "Mod desconocido";
        }

        return name;
    }

    private void showInstalledModsDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Gestor de contenido");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Contenido instalado");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label subtitle = new Label("Administra mods, shaders y paquetes de textura de la instancia actual.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));
        content.setStyle("-fx-background-color: #f6f8fb;");

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        final ComboBox<String> contentTypeBox = new ComboBox<String>();
        contentTypeBox.getItems().addAll("Mods", "Shaders", "Texturas");
        contentTypeBox.getSelectionModel().selectFirst();
        contentTypeBox.setPrefWidth(150);
        contentTypeBox.getStyleClass().add("provider-combo");

        final Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("secondary-button");

        final Button toggleBtn = new Button("Activar / Desactivar");
        toggleBtn.getStyleClass().add("button");
        toggleBtn.setDisable(true);

        final Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setDisable(true);

        final Button openFolderBtn = new Button("Abrir carpeta");
        openFolderBtn.getStyleClass().add("secondary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(
                contentTypeBox,
                refreshBtn,
                toggleBtn,
                deleteBtn,
                spacer,
                openFolderBtn
        );

        final ListView<InstalledContentInfo> list = new ListView<InstalledContentInfo>();
        list.getStyleClass().add("list-view");
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setCellFactory(new javafx.util.Callback<ListView<InstalledContentInfo>, ListCell<InstalledContentInfo>>() {
            @Override
            public ListCell<InstalledContentInfo> call(ListView<InstalledContentInfo> listView) {
                return new ListCell<InstalledContentInfo>() {
                    @Override
                    protected void updateItem(InstalledContentInfo item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(10));
                        row.getStyleClass().add("market-installed-row");

                        StackPane iconBox = new StackPane();
                        iconBox.getStyleClass().add("content-icon-box");
                        iconBox.setMinSize(54, 54);
                        iconBox.setPrefSize(54, 54);
                        iconBox.setMaxSize(54, 54);

                        if (item.icon != null && !item.icon.isError()) {
                            ImageView iconView = new ImageView(item.icon);
                            iconView.setFitWidth(54);
                            iconView.setFitHeight(54);
                            iconView.setPreserveRatio(true);
                            iconView.setSmooth(true);
                            iconBox.getChildren().add(iconView);
                        } else if ((item.iconUrl != null && !item.iconUrl.trim().isEmpty())
                                || (item.projectId != null && !item.projectId.trim().isEmpty())) {
                            ModrinthClient.ModResult fake = new ModrinthClient.ModResult(
                                    item.slug == null ? "" : item.slug,
                                    item.name == null ? "" : item.name,
                                    item.description == null ? "" : item.description,
                                    item.projectId == null ? "" : item.projectId,
                                    item.type == null ? "mod" : item.type
                            );

                            fake.iconUrl = item.iconUrl == null ? "" : item.iconUrl;

                            loadContentIcon(fake, iconBox);
                        } else {
                            Label fallback = new Label(getInstalledContentFallbackIcon(item.type));
                            fallback.getStyleClass().add("content-icon-fallback");
                            iconBox.getChildren().add(fallback);
                        }

                        VBox infoBox = new VBox(7);
                        HBox.setHgrow(infoBox, Priority.ALWAYS);

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);

                        Label nameLabel = new Label(item.name == null ? "Contenido" : item.name);
                        nameLabel.setMaxWidth(430);
                        nameLabel.setStyle(
                                "-fx-font-size: 15px;" +
                                        "-fx-font-weight: 800;" +
                                        "-fx-text-fill: #111827;"
                        );

                        Region topSpacer = new Region();
                        HBox.setHgrow(topSpacer, Priority.ALWAYS);

                        Label providerBadge = new Label(item.provider == null ? "Local" : item.provider);

                        if ("CurseForge".equalsIgnoreCase(item.provider)) {
                            providerBadge.getStyleClass().add("provider-badge-curseforge");
                        } else if ("Modrinth".equalsIgnoreCase(item.provider)) {
                            providerBadge.getStyleClass().add("provider-badge-modrinth");
                        } else {
                            providerBadge.getStyleClass().add("provider-badge-local");
                        }

                        Label typeBadge = new Label(getContentTypeLabel(item.type));
                        typeBadge.getStyleClass().add("content-type-badge");

                        Label stateBadge = new Label(item.enabled ? "Activo" : "Desactivado");
                        stateBadge.getStyleClass().add(item.enabled ? "mod-state-active" : "mod-state-disabled");

                        top.getChildren().addAll(nameLabel, topSpacer, providerBadge, typeBadge, stateBadge);

                        Label descLabel = new Label(item.description == null ? "" : item.description);
                        descLabel.setWrapText(true);
                        descLabel.setMaxWidth(560);
                        descLabel.setStyle(
                                "-fx-font-size: 12px;" +
                                        "-fx-text-fill: #6b7280;"
                        );

                        Label metaLabel = new Label(item.file.getName() + " · " + formatFileSize(item.file.length()));
                        metaLabel.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-text-fill: #9ca3af;"
                        );

                        infoBox.getChildren().addAll(top, descLabel, metaLabel);
                        row.getChildren().addAll(iconBox, infoBox);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        final Label status = new Label("Cargando...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        status.setWrapText(true);

        content.getChildren().addAll(topBar, list, status);

        list.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<InstalledContentInfo>() {
                    @Override
                    public void changed(ObservableValue<? extends InstalledContentInfo> observable,
                                        InstalledContentInfo oldValue,
                                        InstalledContentInfo newValue) {
                        boolean selected = newValue != null;

                        toggleBtn.setDisable(!selected);
                        deleteBtn.setDisable(!selected);

                        if (newValue != null) {
                            toggleBtn.setText(newValue.enabled ? "Desactivar" : "Activar");
                        } else {
                            toggleBtn.setText("Activar / Desactivar");
                        }
                    }
                }
        );

        contentTypeBox.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                toggleBtn.setDisable(true);
                deleteBtn.setDisable(true);
                toggleBtn.setText("Activar / Desactivar");

                String type = getInstalledContentTypeFromDropdown(contentTypeBox);
                refreshInstalledContentPanel(type, list, status);
            }
        });

        refreshBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String type = getInstalledContentTypeFromDropdown(contentTypeBox);
                refreshInstalledContentPanel(type, list, status);
            }
        });

        openFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    String type = getInstalledContentTypeFromDropdown(contentTypeBox);
                    File dir = getContentDirectory(type);
                    dir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dir);
                    }
                } catch (Exception ex) {
                    status.setText("No se pudo abrir carpeta: " + ex.getMessage());
                }
            }
        });

        toggleBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                InstalledContentInfo selected = list.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                try {
                    File newFile = toggleContentEnabled(selected.file);
                    status.setText((selected.enabled ? "Desactivado: " : "Activado: ") + newFile.getName());

                    String type = getInstalledContentTypeFromDropdown(contentTypeBox);
                    refreshInstalledContentPanel(type, list, status);
                } catch (Exception ex) {
                    status.setText("No se pudo cambiar el estado: " + ex.getMessage());
                }
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final InstalledContentInfo selected = list.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Eliminar contenido");
                confirm.setHeaderText("¿Eliminar este archivo?");
                confirm.setContentText(selected.name + "\n\nArchivo: " + selected.file.getName());

                java.util.Optional<ButtonType> result = confirm.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    boolean deleted = selected.file.delete();

                    if (deleted) {
                        removeMarkersForContentFile(selected.file);
                        status.setText("Eliminado: " + selected.name);
                    } else {
                        status.setText("No se pudo eliminar: " + selected.file.getName());
                    }

                    String type = getInstalledContentTypeFromDropdown(contentTypeBox);
                    refreshInstalledContentPanel(type, list, status);
                }
            }
        });

        root.setTop(header);
        root.setCenter(content);

        refreshInstalledContentPanel("mod", list, status);

        Scene scene = new Scene(root, 820, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(760);
        dialog.setMinHeight(560);
        dialog.show();
    }

    private String getInstalledContentTypeFromDropdown(ComboBox<String> box) {
        String value = box.getValue();

        if ("Shaders".equals(value)) {
            return "shader";
        }

        if ("Texturas".equals(value)) {
            return "resourcepack";
        }

        return "mod";
    }

    private static class InstalledContentInfo {
        File file;
        String name;
        String description;
        String provider;
        String type;
        String projectId;
        String slug;
        String iconUrl;
        boolean enabled;
        Image icon;

        InstalledContentInfo(File file, String type) {
            this.file = file;
            this.type = type;
            this.name = file.getName();
            this.description = "Sin descripción disponible.";
            this.provider = "Local";
            this.projectId = "";
            this.slug = "";
            this.iconUrl = "";
            this.enabled = !file.getName().toLowerCase().endsWith(".disabled");
            this.icon = null;
        }
    }

    private static class InstalledContentMarkerInfo {
        String provider = "Local";
        String projectId = "";
        String title = "";
        String slug = "";
        String type = "";
        String iconUrl = "";
    }

    private VBox createInstalledContentTab(final String type) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(18));
        content.setStyle("-fx-background-color: #f6f8fb;");

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        final Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("secondary-button");

        final Button toggleBtn = new Button("Activar / Desactivar");
        toggleBtn.getStyleClass().add("button");
        toggleBtn.setDisable(true);

        final Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setDisable(true);

        final Button openFolderBtn = new Button("Abrir carpeta");
        openFolderBtn.getStyleClass().add("secondary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(refreshBtn, toggleBtn, deleteBtn, spacer, openFolderBtn);

        final ListView<InstalledContentInfo> list = new ListView<InstalledContentInfo>();
        list.getStyleClass().add("list-view");
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setCellFactory(new javafx.util.Callback<ListView<InstalledContentInfo>, ListCell<InstalledContentInfo>>() {
            @Override
            public ListCell<InstalledContentInfo> call(ListView<InstalledContentInfo> listView) {
                return new ListCell<InstalledContentInfo>() {
                    @Override
                    protected void updateItem(InstalledContentInfo item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(10));
                        row.getStyleClass().add("market-installed-row");

                        StackPane iconBox = new StackPane();
                        iconBox.getStyleClass().add("content-icon-box");
                        iconBox.setMinSize(54, 54);
                        iconBox.setPrefSize(54, 54);
                        iconBox.setMaxSize(54, 54);

                        if (item.icon != null && !item.icon.isError()) {
                            ImageView iconView = new ImageView(item.icon);
                            iconView.setFitWidth(54);
                            iconView.setFitHeight(54);
                            iconView.setPreserveRatio(true);
                            iconView.setSmooth(true);
                            iconBox.getChildren().add(iconView);
                        } else if ((item.iconUrl != null && !item.iconUrl.trim().isEmpty())
                                || (item.projectId != null && !item.projectId.trim().isEmpty())) {
                            ModrinthClient.ModResult fake = new ModrinthClient.ModResult(
                                    item.slug == null ? "" : item.slug,
                                    item.name == null ? "" : item.name,
                                    item.description == null ? "" : item.description,
                                    item.projectId == null ? "" : item.projectId,
                                    item.type == null ? "mod" : item.type
                            );

                            fake.iconUrl = item.iconUrl == null ? "" : item.iconUrl;

                            loadContentIcon(fake, iconBox);
                        } else {
                            Label fallback = new Label(getInstalledContentFallbackIcon(item.type));
                            fallback.getStyleClass().add("content-icon-fallback");
                            iconBox.getChildren().add(fallback);
                        }

                        VBox infoBox = new VBox(7);
                        HBox.setHgrow(infoBox, Priority.ALWAYS);

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);

                        Label nameLabel = new Label(item.name == null ? "Contenido" : item.name);
                        nameLabel.setMaxWidth(430);
                        nameLabel.setStyle(
                                "-fx-font-size: 15px;" +
                                        "-fx-font-weight: 800;" +
                                        "-fx-text-fill: #111827;"
                        );

                        Region topSpacer = new Region();
                        HBox.setHgrow(topSpacer, Priority.ALWAYS);

                        Label providerBadge = new Label(item.provider == null ? "Local" : item.provider);

                        if ("CurseForge".equalsIgnoreCase(item.provider)) {
                            providerBadge.getStyleClass().add("provider-badge-curseforge");
                        } else if ("Modrinth".equalsIgnoreCase(item.provider)) {
                            providerBadge.getStyleClass().add("provider-badge-modrinth");
                        } else {
                            providerBadge.getStyleClass().add("provider-badge-local");
                        }

                        Label typeBadge = new Label(getContentTypeLabel(item.type));
                        typeBadge.getStyleClass().add("content-type-badge");

                        Label stateBadge = new Label(item.enabled ? "Activo" : "Desactivado");
                        stateBadge.getStyleClass().add(item.enabled ? "mod-state-active" : "mod-state-disabled");

                        top.getChildren().addAll(nameLabel, topSpacer, providerBadge, typeBadge, stateBadge);

                        Label descLabel = new Label(item.description == null ? "" : item.description);
                        descLabel.setWrapText(true);
                        descLabel.setMaxWidth(560);
                        descLabel.setStyle(
                                "-fx-font-size: 12px;" +
                                        "-fx-text-fill: #6b7280;"
                        );

                        Label metaLabel = new Label(item.file.getName() + " · " + formatFileSize(item.file.length()));
                        metaLabel.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-text-fill: #9ca3af;"
                        );

                        infoBox.getChildren().addAll(top, descLabel, metaLabel);
                        row.getChildren().addAll(iconBox, infoBox);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        final Label status = new Label("Cargando...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        status.setWrapText(true);

        content.getChildren().addAll(topBar, list, status);

        list.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<InstalledContentInfo>() {
                    @Override
                    public void changed(ObservableValue<? extends InstalledContentInfo> observable,
                                        InstalledContentInfo oldValue,
                                        InstalledContentInfo newValue) {
                        boolean selected = newValue != null;

                        toggleBtn.setDisable(!selected);
                        deleteBtn.setDisable(!selected);

                        if (newValue != null) {
                            toggleBtn.setText(newValue.enabled ? "Desactivar" : "Activar");
                        } else {
                            toggleBtn.setText("Activar / Desactivar");
                        }
                    }
                }
        );

        refreshBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                refreshInstalledContentPanel(type, list, status);
            }
        });

        openFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File dir = getContentDirectory(type);
                    dir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dir);
                    }
                } catch (Exception ex) {
                    status.setText("No se pudo abrir carpeta: " + ex.getMessage());
                }
            }
        });

        toggleBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                InstalledContentInfo selected = list.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                try {
                    File newFile = toggleContentEnabled(selected.file);
                    status.setText((selected.enabled ? "Desactivado: " : "Activado: ") + newFile.getName());
                    refreshInstalledContentPanel(type, list, status);
                } catch (Exception ex) {
                    status.setText("No se pudo cambiar el estado: " + ex.getMessage());
                }
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final InstalledContentInfo selected = list.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Eliminar contenido");
                confirm.setHeaderText("¿Eliminar este archivo?");
                confirm.setContentText(selected.name + "\n\nArchivo: " + selected.file.getName());

                java.util.Optional<ButtonType> result = confirm.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    boolean deleted = selected.file.delete();

                    if (deleted) {
                        removeMarkersForContentFile(selected.file);
                        status.setText("Eliminado: " + selected.name);
                    } else {
                        status.setText("No se pudo eliminar: " + selected.file.getName());
                    }

                    refreshInstalledContentPanel(type, list, status);
                }
            }
        });

        refreshInstalledContentPanel(type, list, status);

        return content;
    }

    private void refreshInstalledContentPanel(final String type,
                                              final ListView<InstalledContentInfo> list,
                                              final Label statusLabel) {
        File dir = getContentDirectory(type);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        list.getItems().clear();

        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();

                if (lower.endsWith(".disabled")) {
                    return true;
                }

                if ("mod".equalsIgnoreCase(type)) {
                    return lower.endsWith(".jar");
                }

                return lower.endsWith(".zip");
            }
        });

        if (files == null || files.length == 0) {
            statusLabel.setText("No hay " + getContentTypeLabelPlural(type).toLowerCase() + " instalados.");
            return;
        }

        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        int active = 0;
        int disabled = 0;

        for (File file : files) {
            InstalledContentInfo info = readInstalledContentInfo(file, type);

            if (info.enabled) {
                active++;
            } else {
                disabled++;
            }

            list.getItems().add(info);
        }

        statusLabel.setText(
                getContentTypeLabelPlural(type) +
                        ": " + files.length +
                        " · Activos: " + active +
                        " · Desactivados: " + disabled
        );
    }

    private InstalledContentInfo readInstalledContentInfo(File file, String type) {
        InstalledContentInfo info = new InstalledContentInfo(file, type);

        InstalledContentMarkerInfo marker = readInstalledContentMarkerInfo(file);

        info.provider = marker.provider;
        info.projectId = marker.projectId;
        info.slug = marker.slug;
        info.iconUrl = marker.iconUrl;

        /*
         * Para mods .jar, preferimos SIEMPRE la metadata real del mod
         * antes que el marcador del launcher.
         *
         * Esto evita que Sodium aparezca como AANobbMI,
         * que es su projectId de Modrinth.
         */
        if ("mod".equalsIgnoreCase(type)) {
            InstalledModInfo modInfo = readInstalledModInfo(file);

            boolean hasRealModName = modInfo.name != null
                    && !modInfo.name.trim().isEmpty()
                    && !"Mod desconocido".equalsIgnoreCase(modInfo.name.trim())
                    && !modInfo.name.equals(file.getName());

            if (hasRealModName) {
                info.name = modInfo.name;
            } else if (marker.title != null && !marker.title.trim().isEmpty() && !looksLikeProjectId(marker.title)) {
                info.name = marker.title;
            } else {
                info.name = cleanModFileName(file.getName());
            }

            info.description = modInfo.description;
            info.icon = modInfo.icon;

            if (marker.provider == null || "Local".equalsIgnoreCase(marker.provider)) {
                info.provider = modInfo.provider == null ? "Local" : modInfo.provider;
            }

            info.enabled = modInfo.enabled;

            return info;
        }

        /*
         * Para shaders/resourcepacks sí usamos el marcador,
         * porque los ZIP normalmente no tienen fabric.mod.json.
         */
        if (marker.title != null && !marker.title.trim().isEmpty() && !looksLikeProjectId(marker.title)) {
            info.name = marker.title;
        } else {
            info.name = cleanContentFileName(file.getName());
        }

        info.description = getInstalledContentDescription(type);
        info.enabled = !file.getName().toLowerCase().endsWith(".disabled");

        return info;
    }

    private boolean looksLikeProjectId(String text) {
        if (text == null) {
            return false;
        }

        String value = text.trim();

        if (value.isEmpty()) {
            return false;
        }

        /*
         * IDs de Modrinth suelen ser cadenas cortas aleatorias:
         * AANobbMI, PtjYWJkn, etc.
         */
        if (value.matches("[A-Za-z0-9]{6,12}")) {
            return true;
        }

        /*
         * IDs internos de CurseForge suelen venir como curseforge:123456
         */
        if (value.toLowerCase().startsWith("curseforge:")) {
            return true;
        }

        return false;
    }

    private InstalledContentMarkerInfo readInstalledContentMarkerInfo(File file) {
        InstalledContentMarkerInfo info = new InstalledContentMarkerInfo();

        if (file == null) {
            return info;
        }

        File registry = getContentRegistryDirectory();

        if (!registry.exists()) {
            return info;
        }

        File[] markers = registry.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".txt");
            }
        });

        if (markers == null) {
            return info;
        }

        String filePath;

        try {
            filePath = file.getCanonicalPath();
        } catch (Exception ex) {
            filePath = file.getAbsolutePath();
        }

        for (File marker : markers) {
            java.io.BufferedReader reader = null;

            try {
                reader = new java.io.BufferedReader(new java.io.FileReader(marker));

                String line;
                boolean fileMatches = false;

                InstalledContentMarkerInfo current = new InstalledContentMarkerInfo();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("file=")) {
                        String markerPath = line.substring("file=".length()).trim();

                        try {
                            markerPath = new File(markerPath).getCanonicalPath();
                        } catch (Exception ignored) {
                        }

                        if (markerPath.equals(filePath)) {
                            fileMatches = true;
                        }
                    } else if (line.startsWith("projectId=")) {
                        current.projectId = line.substring("projectId=".length()).trim();
                    } else if (line.startsWith("title=")) {
                        current.title = line.substring("title=".length()).trim();
                    } else if (line.startsWith("slug=")) {
                        current.slug = line.substring("slug=".length()).trim();
                    } else if (line.startsWith("type=")) {
                        current.type = line.substring("type=".length()).trim();
                    } else if (line.startsWith("iconUrl=")) {
                        current.iconUrl = line.substring("iconUrl=".length()).trim();
                    } else if (line.startsWith("provider=")) {
                        current.provider = line.substring("provider=".length()).trim();
                    }
                }

                if (fileMatches) {
                    if (current.provider == null || current.provider.trim().isEmpty() || "Local".equalsIgnoreCase(current.provider)) {
                        if (current.projectId != null && current.projectId.toLowerCase().startsWith("curseforge:")) {
                            current.provider = "CurseForge";
                        } else if (current.projectId != null && !current.projectId.trim().isEmpty()) {
                            current.provider = "Modrinth";
                        } else {
                            current.provider = "Local";
                        }
                    }

                    return current;
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return info;
    }

    private String getInstalledContentFallbackIcon(String type) {
        if ("shader".equalsIgnoreCase(type)) {
            return "✨";
        }

        if ("resourcepack".equalsIgnoreCase(type)) {
            return "🖼";
        }

        return "📦";
    }

    private String getInstalledContentDescription(String type) {
        if ("shader".equalsIgnoreCase(type)) {
            return "Shader instalado en la instancia actual.";
        }

        if ("resourcepack".equalsIgnoreCase(type)) {
            return "Paquete de texturas instalado en la instancia actual.";
        }

        return "Contenido instalado en la instancia actual.";
    }

    private String cleanContentFileName(String fileName) {
        if (fileName == null) {
            return "Contenido desconocido";
        }

        String name = fileName;

        if (name.toLowerCase().endsWith(".zip.disabled")) {
            name = name.substring(0, name.length() - ".zip.disabled".length());
        } else if (name.toLowerCase().endsWith(".jar.disabled")) {
            name = name.substring(0, name.length() - ".jar.disabled".length());
        } else if (name.toLowerCase().endsWith(".disabled")) {
            name = name.substring(0, name.length() - ".disabled".length());
        } else if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - ".zip".length());
        } else if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - ".jar".length());
        }

        name = name.replace("-", " ").replace("_", " ").trim();

        if (name.isEmpty()) {
            return "Contenido desconocido";
        }

        return name;
    }

    private File toggleContentEnabled(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("El archivo no existe.");
        }

        String name = file.getName();
        File target;

        if (name.toLowerCase().endsWith(".disabled")) {
            target = new File(file.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        } else {
            target = new File(file.getParentFile(), name + ".disabled");
        }

        if (target.exists()) {
            throw new Exception("Ya existe un archivo con el nombre destino: " + target.getName());
        }

        boolean renamed = file.renameTo(target);

        if (!renamed) {
            throw new Exception("No se pudo renombrar el archivo.");
        }

        return target;
    }

    private String cleanConsoleText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned = text;

        // Normalizar saltos.
        cleaned = cleaned.replace("\r\n", "\n");
        cleaned = cleaned.replace("\r", "\n");

        // Eliminar BOM.
        cleaned = cleaned.replace("\uFEFF", "");

        // Eliminar ANSI real.
        cleaned = cleaned.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");

        // Eliminar ANSI OSC.
        cleaned = cleaned.replaceAll("\\u001B\\].*?(\\u0007|\\u001B\\\\)", "");

        // Eliminar ANSI parcial tipo [32m, [0m, [1;31m.
        cleaned = cleaned.replaceAll("\\[[0-9;]*m", "");

        // Eliminar colores Minecraft.
        cleaned = cleaned.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        cleaned = cleaned.replaceAll("Â§[0-9A-FK-ORa-fk-or]", "");

        // Eliminar caracter de reemplazo.
        cleaned = cleaned.replace("\uFFFD", "");

        String[] lines = cleaned.split("\n", -1);
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            String safe = makeConsoleLineSafe(line);

            if (safe == null || safe.isEmpty()) {
                continue;
            }

            out.append(safe).append("\n");
        }

        return out.toString();
    }

    private String makeConsoleLineSafe(String line) {
        if (line == null) {
            return "";
        }

        line = line.trim();

        if (line.isEmpty()) {
            return "";
        }

        int invalid = 0;
        int total = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            total++;

            if (!isSafeConsoleChar(c)) {
                invalid++;
            }
        }

        if (total > 0) {
            double ratio = invalid / (double) total;

            // Si más del 15% de la línea son símbolos raros, la ocultamos.
            if (ratio > 0.15) {
                return "[linea corrupta omitida]";
            }
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (isSafeConsoleChar(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }

        return sb.toString()
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private boolean isSafeConsoleChar(char c) {
        // ASCII normal.
        if (c >= 32 && c <= 126) {
            return true;
        }

        // Tab.
        if (c == '\t') {
            return true;
        }

        // Español común.
        String allowed = "áéíóúÁÉÍÓÚñÑüÜçÇ¿¡€ºª";

        if (allowed.indexOf(c) >= 0) {
            return true;
        }

        // Símbolos básicos seguros.
        String symbols = "·•+-_=()[]{}.,:;/\\|<>!?%#@";

        if (symbols.indexOf(c) >= 0) {
            return true;
        }

        return false;
    }

    private static String makeGameConsoleLineSafe(String line) {
        if (line == null) {
            return "";
        }

        line = line.trim();

        if (line.isEmpty()) {
            return "";
        }

        int invalid = 0;
        int total = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            total++;

            if (!isSafeGameConsoleChar(c)) {
                invalid++;
            }
        }

        if (total > 0) {
            double ratio = invalid / (double) total;

            if (ratio > 0.15) {
                return "[linea corrupta omitida]";
            }
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (isSafeGameConsoleChar(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }

        return sb.toString()
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private static boolean isSafeGameConsoleChar(char c) {
        if (c >= 32 && c <= 126) {
            return true;
        }

        if (c == '\t') {
            return true;
        }

        String allowed = "áéíóúÁÉÍÓÚñÑüÜçÇ¿¡€ºª";

        if (allowed.indexOf(c) >= 0) {
            return true;
        }

        String symbols = "·•+-_=()[]{}.,:;/\\|<>!?%#@";

        if (symbols.indexOf(c) >= 0) {
            return true;
        }

        return false;
    }

    private boolean looksLikeBinaryGarbage(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();

        if (trimmed.length() < 24) {
            return false;
        }

        int weird = 0;
        int total = 0;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            total++;

            if (!isAllowedConsoleChar(c)) {
                weird++;
            }
        }

        if (total == 0) {
            return false;
        }

        double ratio = weird / (double) total;

        return ratio > 0.35;
    }

    private boolean isAllowedConsoleChar(char c) {
        // ASCII normal imprimible
        if (c >= 32 && c <= 126) {
            return true;
        }

        // Saltos/tab
        if (c == '\n' || c == '\t') {
            return true;
        }

        // Caracteres españoles/comunes
        String allowed = "áéíóúÁÉÍÓÚñÑüÜçÇ¿¡€ºª";

        if (allowed.indexOf(c) >= 0) {
            return true;
        }

        // Algunos símbolos útiles
        String symbols = "·•✓✔✕✖→←↑↓";

        if (symbols.indexOf(c) >= 0) {
            return true;
        }

        return false;
    }

    private void showContentSearchDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Buscar contenido");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Buscar contenido");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        String selectedVersion = getCurrentMinecraftVersionForMods();

        Label subtitle = new Label("Descarga mods, paquetes de textura y shaders para Minecraft " + selectedVersion + ".");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        final ComboBox<ContentProviderOption> providerBox = createProviderComboBox();

        final ComboBox<String> typeBox = new ComboBox<String>();
        typeBox.getItems().addAll("Mods", "Texturas", "Shaders", "Modpacks");
        typeBox.getSelectionModel().selectFirst();
        typeBox.setPrefWidth(135);

        final TextField searchField = new TextField();
        searchField.setPromptText("Buscar contenido...");
        searchField.getStyleClass().add("text-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        final Button searchBtn = new Button("Buscar");
        searchBtn.getStyleClass().add("button");

        final Button curseForgeKeyBtn = new Button("API Key");
        curseForgeKeyBtn.setVisible(false);
        curseForgeKeyBtn.setManaged(false);
        curseForgeKeyBtn.getStyleClass().add("api-key-button");

        searchBox.getChildren().addAll(providerBox, typeBox, searchField, searchBtn, curseForgeKeyBtn);

        final ListView<ModrinthClient.ModResult> resultsList = new ListView<ModrinthClient.ModResult>();
        resultsList.getStyleClass().add("list-view");
        VBox.setVgrow(resultsList, Priority.ALWAYS);

        resultsList.setCellFactory(new javafx.util.Callback<ListView<ModrinthClient.ModResult>, ListCell<ModrinthClient.ModResult>>() {
            @Override
            public ListCell<ModrinthClient.ModResult> call(ListView<ModrinthClient.ModResult> listView) {
                return new ListCell<ModrinthClient.ModResult>() {
                    @Override
                    protected void updateItem(ModrinthClient.ModResult item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(10));

                        StackPane iconBox = new StackPane();
                        iconBox.getStyleClass().add("content-icon-box");
                        iconBox.setMinSize(54, 54);
                        iconBox.setPrefSize(54, 54);
                        iconBox.setMaxSize(54, 54);

                        loadContentIcon(item, iconBox);

                        VBox infoBox = new VBox(7);
                        HBox.setHgrow(infoBox, Priority.ALWAYS);

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);

                        Label nameLabel = new Label(item.title);
                        nameLabel.setMaxWidth(430);
                        nameLabel.setStyle(
                                "-fx-font-size: 15px;" +
                                        "-fx-font-weight: 800;" +
                                        "-fx-text-fill: #111827;"
                        );

                        Region topSpacer = new Region();
                        HBox.setHgrow(topSpacer, Priority.ALWAYS);

                        Label typeLabel = new Label(getContentTypeLabel(item.projectType));
                        typeLabel.getStyleClass().add("content-type-badge");

                        Label providerLabel = new Label(getProviderLabelForResult(item));
                        providerLabel.getStyleClass().add(getProviderStyleClassForResult(item));

                        top.getChildren().addAll(nameLabel, topSpacer, providerLabel, typeLabel);

                        Label descLabel = new Label(item.description == null ? "" : item.description);
                        descLabel.setWrapText(true);
                        descLabel.setMaxWidth(560);
                        descLabel.setStyle(
                                "-fx-font-size: 12px;" +
                                        "-fx-text-fill: #6b7280;"
                        );

                        Label idLabel = new Label("Slug: " + item.slug + " · Project ID: " + item.projectId);
                        idLabel.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-text-fill: #9ca3af;"
                        );

                        infoBox.getChildren().addAll(top, descLabel, idLabel);
                        row.getChildren().addAll(iconBox, infoBox);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        final Button installBtn = new Button("Instalar seleccionado");
        installBtn.getStyleClass().add("launch-button");
        installBtn.setDisable(true);

        final Button openTargetFolderBtn = new Button("Abrir carpeta destino");
        openTargetFolderBtn.getStyleClass().add("secondary-button");

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(installBtn, openTargetFolderBtn, bottomSpacer);

        final Label status = new Label("Cargando contenido popular...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        status.setWrapText(true);

        final ProgressBar downloadProgressBar = new ProgressBar(0);
        downloadProgressBar.setMaxWidth(Double.MAX_VALUE);
        downloadProgressBar.setVisible(false);

        content.getChildren().addAll(searchBox, resultsList, status, downloadProgressBar, bottomBar);

        curseForgeKeyBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                showCurseForgeApiKeyDialog();

                if (hasCurseForgeApiKey()) {
                    status.setText("CurseForge API Key configurada.");
                } else {
                    status.setText("CurseForge seleccionado, pero falta configurar la API Key.");
                }
            }
        });

        resultsList.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<ModrinthClient.ModResult>() {
                    @Override
                    public void changed(ObservableValue<? extends ModrinthClient.ModResult> observable,
                                        ModrinthClient.ModResult oldValue,
                                        ModrinthClient.ModResult newValue) {
                        updateContentInstallButtonState(newValue, installBtn, status);
                    }
                }
        );

        EventHandler<ActionEvent> doSearch = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final String query = searchField.getText() == null ? "" : searchField.getText().trim();
                final ContentProviderOption provider = providerBox.getValue();

                if (isCurseForgeProvider(provider)) {
                    if (!ensureCurseForgeApiKey()) {
                        resultsList.getItems().clear();
                        installBtn.setText("Instalar seleccionado");
                        installBtn.setDisable(true);
                        searchBtn.setDisable(false);

                        status.setText("CurseForge seleccionado, pero falta configurar la API Key.");
                        return;
                    }

                    final String apiKey = getCurseForgeApiKey();
                    final String type = getSelectedContentType(typeBox);

                    if (query.isEmpty()) {
                        loadProviderPopularContent(providerBox, typeBox, resultsList, searchBtn, installBtn, status);
                        return;
                    }

                    resultsList.getItems().clear();
                    searchBtn.setDisable(true);
                    installBtn.setDisable(true);
                    installBtn.setText("Instalar seleccionado");

                    status.setText("Buscando " + getContentTypeLabel(type).toLowerCase() + " en CurseForge...");

                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final List<ModrinthClient.ModResult> results = CurseForgeClient.searchProjects(apiKey, query, type);

                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        resultsList.getItems().setAll(results);
                                        searchBtn.setDisable(false);

                                        if (results.isEmpty()) {
                                            status.setText("No se encontraron resultados en CurseForge.");
                                        } else {
                                            status.setText("Se encontraron " + results.size() + " resultados en CurseForge.");
                                        }
                                    }
                                });
                            } catch (final Exception ex) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        searchBtn.setDisable(false);
                                        status.setText("Error buscando en CurseForge: " + ex.getMessage());
                                    }
                                });
                            }
                        }
                    }, "CurseForge-Search");

                    t.setDaemon(true);
                    t.start();
                    return;
                }

                if (query.isEmpty()) {
                    loadProviderPopularContent(providerBox, typeBox, resultsList, searchBtn, installBtn, status);
                    return;
                }

                final String type = getSelectedContentType(typeBox);

                resultsList.getItems().clear();
                searchBtn.setDisable(true);
                installBtn.setDisable(true);
                installBtn.setText("Instalar seleccionado");

                status.setText("Buscando " + getContentTypeLabel(type).toLowerCase() + " en Modrinth...");

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<ModrinthClient.ModResult> results = ModrinthClient.searchProjects(query, type);

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    resultsList.getItems().setAll(results);
                                    searchBtn.setDisable(false);

                                    if (results.isEmpty()) {
                                        status.setText("No se encontraron resultados.");
                                    } else {
                                        status.setText("Se encontraron " + results.size() + " resultados.");
                                    }
                                }
                            });
                        } catch (final Exception ex) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchBtn.setDisable(false);
                                    status.setText("Error buscando contenido: " + ex.getMessage());
                                }
                            });
                        }
                    }
                }, "Modrinth-Universal-Search");

                t.setDaemon(true);
                t.start();
            }
        };

        searchBtn.setOnAction(doSearch);
        searchField.setOnAction(doSearch);

        typeBox.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                searchField.clear();
                loadProviderPopularContent(providerBox, typeBox, resultsList, searchBtn, installBtn, status);
            }
        });

        providerBox.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                ContentProviderOption provider = providerBox.getValue();

                boolean curseForge = isCurseForgeProvider(provider);

                curseForgeKeyBtn.setVisible(curseForge);
                curseForgeKeyBtn.setManaged(curseForge);

                if (isModrinthProvider(provider)) {
                    searchField.setPromptText("Buscar en Modrinth...");
                } else {
                    searchField.setPromptText("Buscar en CurseForge...");
                }

                searchField.clear();
                installBtn.setText("Instalar seleccionado");
                installBtn.setDisable(true);

                loadProviderPopularContent(providerBox, typeBox, resultsList, searchBtn, installBtn, status);
            }
        });

        installBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final ContentProviderOption provider = providerBox.getValue();
                final ModrinthClient.ModResult selected = resultsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    status.setText("Selecciona un resultado primero.");
                    return;
                }

                if (isContentInstalled(selected.projectId, selected.projectType) || isLogicalContentInstalled(selected)) {
                    installBtn.setText("Ya instalado");
                    installBtn.setDisable(true);
                    status.setText("Este contenido o un equivalente ya está instalado.");
                    return;
                }

                final String mcVersion = getCurrentMinecraftVersionForMods();

                final ModrinthClient.ModVersionFile selectedVersionFile;

                try {
                    selectedVersionFile = showContentVersionSelectionDialog(selected, provider, mcVersion);
                } catch (Exception ex) {
                    status.setText("Error cargando versiones: " + ex.getMessage());
                    return;
                }

                if (selectedVersionFile == null) {
                    status.setText("Instalación cancelada.");
                    return;
                }

                if ("modpack".equalsIgnoreCase(selected.projectType)) {
                    installSelectedModpackVersion(selected, selectedVersionFile, status, downloadProgressBar);
                    return;
                }

                installBtn.setDisable(true);
                searchBtn.setDisable(true);

                if (isCurseForgeProvider(provider)) {
                    if (!ensureCurseForgeApiKey()) {
                        installBtn.setDisable(false);
                        searchBtn.setDisable(false);
                        status.setText("Falta configurar la API Key de CurseForge.");
                        return;
                    }

                    status.setText("Instalando desde CurseForge: " + selected.title + "...");

                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final File installedFile = installSelectedContentFile(
                                        selected,
                                        selectedVersionFile,
                                        status,
                                        downloadProgressBar
                                );

                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        searchBtn.setDisable(false);
                                        installBtn.setText("Ya instalado");
                                        installBtn.setDisable(true);
                                        status.setText("Instalado desde CurseForge: " + installedFile.getName());
                                        showToast("Instalado: " + installedFile.getName(), "success");
                                        downloadProgressBar.setVisible(false);
                                        downloadProgressBar.setProgress(0);
                                    }
                                });
                            } catch (final Exception ex) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        searchBtn.setDisable(false);
                                        installBtn.setText("Instalar seleccionado");
                                        installBtn.setDisable(false);
                                        status.setText("Error instalando desde CurseForge: " + ex.getMessage());
                                        ex.printStackTrace();
                                        downloadProgressBar.setVisible(false);
                                        downloadProgressBar.setProgress(0);
                                        showToast("Error instalando contenido", "error");
                                        downloadProgressBar.setVisible(false);
                                        downloadProgressBar.setProgress(0);
                                    }
                                });
                            }
                        }
                    }, "CurseForge-Install");

                    t.setDaemon(true);
                    t.start();
                    return;
                }

                status.setText("Instalando desde Modrinth: " + selected.title + "...");
                downloadProgressBar.setVisible(false);
                downloadProgressBar.setProgress(0);

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final File installedFile = installSelectedContentFile(
                                    selected,
                                    selectedVersionFile,
                                    status,
                                    downloadProgressBar
                            );

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchBtn.setDisable(false);
                                    installBtn.setText("Ya instalado");
                                    installBtn.setDisable(true);
                                    status.setText("Instalado correctamente junto con sus dependencias: " + installedFile.getName());
                                    showToast("Instalado: " + installedFile.getName(), "success");
                                    downloadProgressBar.setVisible(false);
                                    downloadProgressBar.setProgress(0);
                                }
                            });
                        } catch (final Exception ex) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchBtn.setDisable(false);

                                    String msg = ex.getMessage() == null ? "Error desconocido" : ex.getMessage();

                                    if (msg.toLowerCase().contains("ya está instalado")) {
                                        installBtn.setText("Ya instalado");
                                        installBtn.setDisable(true);
                                        status.setText(msg);
                                    } else {
                                        installBtn.setText("Instalar seleccionado");
                                        installBtn.setDisable(false);
                                        status.setText("Error instalando desde Modrinth: " + msg);
                                    }

                                    ex.printStackTrace();
                                }
                            });
                        }
                    }
                }, "Modrinth-Universal-Install");

                t.setDaemon(true);
                t.start();
            }
        });



        openTargetFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    String type = getSelectedContentType(typeBox);
                    File dir = getContentDirectory(type);
                    dir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dir);
                    }
                } catch (Exception ex) {
                    status.setText("No se pudo abrir la carpeta: " + ex.getMessage());
                }
            }
        });

        root.setTop(header);
        root.setCenter(content);

        Scene scene = new Scene(root, 760, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(560);
        dialog.show();

        loadProviderPopularContent(providerBox, typeBox, resultsList, searchBtn, installBtn, status);
    }

    private String getProviderLabelForResult(ModrinthClient.ModResult item) {
        if (item == null || item.projectId == null) {
            return "Modrinth";
        }

        if (item.projectId.toLowerCase().startsWith("curseforge:")) {
            return "CurseForge";
        }

        return "Modrinth";
    }

    private String getProviderStyleClassForResult(ModrinthClient.ModResult item) {
        if (item != null && item.projectId != null && item.projectId.toLowerCase().startsWith("curseforge:")) {
            return "provider-badge-curseforge";
        }

        return "provider-badge-modrinth";
    }

    private void showSettingsDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Ajustes");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Ajustes");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label subtitle = new Label("Configura el launcher, proveedores, caché y carpetas.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");

        VBox content = new VBox(16);
        content.setPadding(new Insets(0, 24, 20, 24));

        /*
         * PROVIDERS
         */
        VBox providersCard = new VBox(12);
        providersCard.getStyleClass().add("namemc-card");

        Label providersTitle = new Label("Proveedores");
        providersTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label curseForgeStatus = new Label(hasCurseForgeApiKey()
                ? "CurseForge API Key configurada."
                : "CurseForge API Key no configurada.");
        curseForgeStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        Button configureCurseForgeBtn = new Button("Configurar CurseForge API Key");
        configureCurseForgeBtn.getStyleClass().add("button");

        configureCurseForgeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                showCurseForgeApiKeyDialog();
                curseForgeStatus.setText(hasCurseForgeApiKey()
                        ? "CurseForge API Key configurada."
                        : "CurseForge API Key no configurada.");
            }
        });

        providersCard.getChildren().addAll(providersTitle, curseForgeStatus, configureCurseForgeBtn);

        /*
         * APPEARANCE
         */
        VBox appearanceCard = new VBox(12);
        appearanceCard.getStyleClass().add("namemc-card");

        Label appearanceTitle = new Label("Apariencia");
        appearanceTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        final ComboBox<String> themeBox = new ComboBox<String>();
        themeBox.getItems().addAll("Claro", "Oscuro (próximamente)");
        themeBox.getSelectionModel().select(prefs.get("theme", "Claro"));
        themeBox.setMaxWidth(Double.MAX_VALUE);

        Label themeHint = new Label("El tema oscuro quedará preparado para el siguiente paso.");
        themeHint.setWrapText(true);
        themeHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        themeBox.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String selected = themeBox.getValue();

                if (selected == null) {
                    selected = "Claro";
                }

                prefs.put("theme", selected);
                statusLabel.setText("Tema seleccionado: " + selected);
            }
        });

        appearanceCard.getChildren().addAll(appearanceTitle, themeBox, themeHint);

        /*
         * FOLDERS
         */
        VBox foldersCard = new VBox(12);
        foldersCard.getStyleClass().add("namemc-card");

        Label foldersTitle = new Label("Carpetas");
        foldersTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label launcherPath = new Label("Launcher: " + getLauncherDataDir().getAbsolutePath());
        launcherPath.setWrapText(true);
        launcherPath.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label instancesPath = new Label("Instancias: " + InstanceManager.getBaseDir().getAbsolutePath());
        instancesPath.setWrapText(true);
        instancesPath.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        HBox folderButtons = new HBox(10);

        Button openLauncherFolderBtn = new Button("Abrir launcher");
        openLauncherFolderBtn.getStyleClass().add("secondary-button");

        Button openInstancesFolderBtn = new Button("Abrir instancias");
        openInstancesFolderBtn.getStyleClass().add("secondary-button");

        Button openMinecraftFolderBtn = new Button("Abrir .minecraft");
        openMinecraftFolderBtn.getStyleClass().add("secondary-button");

        folderButtons.getChildren().addAll(openLauncherFolderBtn, openInstancesFolderBtn, openMinecraftFolderBtn);

        openLauncherFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(getLauncherDataDir());
            }
        });

        openInstancesFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(InstanceManager.getBaseDir());
            }
        });

        openMinecraftFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(VersionManager.MC_DIR);
            }
        });

        foldersCard.getChildren().addAll(foldersTitle, launcherPath, instancesPath, folderButtons);

        /*
         * CACHE
         */
        VBox cacheCard = new VBox(12);
        cacheCard.getStyleClass().add("namemc-card");

        Label cacheTitle = new Label("Caché y logs");
        cacheTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label cacheInfo = new Label("Puedes limpiar la caché de iconos o borrar el log del launcher.");
        cacheInfo.setWrapText(true);
        cacheInfo.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        HBox cacheButtons = new HBox(10);

        Button clearIconCacheBtn = new Button("Limpiar iconos");
        clearIconCacheBtn.getStyleClass().add("secondary-button");

        Button clearLogsBtn = new Button("Limpiar logs");
        clearLogsBtn.getStyleClass().add("secondary-button");

        cacheButtons.getChildren().addAll(clearIconCacheBtn, clearLogsBtn);

        clearIconCacheBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    deleteDirectory(new File(getLauncherDataDir(), "icon-cache"));
                    statusLabel.setText("Caché de iconos limpiada.");
                } catch (Exception ex) {
                    statusLabel.setText("Error limpiando caché: " + ex.getMessage());
                }
            }
        });

        clearLogsBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File logFile = new File(getLauncherDataDir(), "launcher.log");

                    if (logFile.exists()) {
                        logFile.delete();
                    }

                    statusLabel.setText("Logs limpiados. Reinicia el launcher para recrear el archivo.");
                } catch (Exception ex) {
                    statusLabel.setText("Error limpiando logs: " + ex.getMessage());
                }
            }
        });

        cacheCard.getChildren().addAll(cacheTitle, cacheInfo, cacheButtons);

        /*
         * JAVA RUNTIMES
         */
        VBox javaCard = new VBox(12);
        javaCard.getStyleClass().add("namemc-card");

        Label javaTitle = new Label("Java runtimes");
        javaTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label javaInfo = new Label("Los runtimes Java se descargan automáticamente cuando una versión de Minecraft lo requiere.");
        javaInfo.setWrapText(true);
        javaInfo.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        Button openRuntimesBtn = new Button("Abrir runtimes");
        openRuntimesBtn.getStyleClass().add("secondary-button");

        openRuntimesBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(new File(getLauncherDataDir(), "runtimes"));
            }
        });

        javaCard.getChildren().addAll(javaTitle, javaInfo, openRuntimesBtn);

        /*
         * SYSTEM
         */
        VBox systemCard = new VBox(12);
        systemCard.getStyleClass().add("namemc-card");

        Label systemTitle = new Label("Sistema");
        systemTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label osLabel = new Label("Sistema operativo: " + PlatformManager.getOS());
        osLabel.setWrapText(true);
        osLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

        Label archLabel = new Label("Arquitectura: " + PlatformManager.getAdoptiumArch());
        archLabel.setWrapText(true);
        archLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

        Label mcDirLabel = new Label("Minecraft: " + VersionManager.MC_DIR.getAbsolutePath());
        mcDirLabel.setWrapText(true);
        mcDirLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label launcherDirLabel = new Label("Launcher: " + getLauncherDataDir().getAbsolutePath());
        launcherDirLabel.setWrapText(true);
        launcherDirLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label instancesDirLabel = new Label("Instancias: " + InstanceManager.getBaseDir().getAbsolutePath());
        instancesDirLabel.setWrapText(true);
        instancesDirLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label runtimesDirLabel = new Label("Runtimes Java: " + new File(getLauncherDataDir(), "runtimes").getAbsolutePath());
        runtimesDirLabel.setWrapText(true);
        runtimesDirLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        HBox systemButtonsRow1 = new HBox(10);
        HBox systemButtonsRow2 = new HBox(10);

        Button openMcDirBtn = new Button("Abrir Minecraft");
        openMcDirBtn.getStyleClass().add("secondary-button");

        Button openLauncherDirBtn = new Button("Abrir launcher");
        openLauncherDirBtn.getStyleClass().add("secondary-button");

        Button openInstancesDirBtn = new Button("Abrir instancias");
        openInstancesDirBtn.getStyleClass().add("secondary-button");

        Button openRuntimesDirBtn = new Button("Abrir runtimes");
        openRuntimesDirBtn.getStyleClass().add("secondary-button");

        systemButtonsRow1.getChildren().addAll(openMcDirBtn, openLauncherDirBtn);
        systemButtonsRow2.getChildren().addAll(openInstancesDirBtn, openRuntimesDirBtn);

        openMcDirBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(VersionManager.MC_DIR);
            }
        });

        openLauncherDirBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(getLauncherDataDir());
            }
        });

        openInstancesDirBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(InstanceManager.getBaseDir());
            }
        });

        openRuntimesDirBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(new File(getLauncherDataDir(), "runtimes"));
            }
        });

        systemCard.getChildren().addAll(
                systemTitle,
                osLabel,
                archLabel,
                mcDirLabel,
                launcherDirLabel,
                instancesDirLabel,
                runtimesDirLabel,
                systemButtonsRow1,
                systemButtonsRow2
        );

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("button");

        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        footer.getChildren().add(closeBtn);

        content.getChildren().addAll(
                providersCard,
                appearanceCard,
                foldersCard,
                cacheCard,
                javaCard,
                systemCard,
                footer
        );

        scroll.setContent(content);

        root.setTop(header);
        root.setCenter(scroll);

        Scene scene = new Scene(root, 720, 680);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(640);
        dialog.setMinHeight(580);
        dialog.show();
    }

    private File getLauncherDataDir() {
        File dir = PlatformManager.getLauncherDataDir();

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private void openFolder(File folder) {
        try {
            PlatformManager.openFolder(folder);
        } catch (Exception ex) {
            statusLabel.setText("No se pudo abrir carpeta: " + ex.getMessage());
        }
    }

    private void deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }

        if (!file.delete()) {
            System.err.println("No se pudo borrar: " + file.getAbsolutePath());
        }
    }

    private String getContentLogicalKey(ModrinthClient.ModResult item) {
        if (item == null) {
            return "";
        }

        String base = "";

        if (item.slug != null && !item.slug.trim().isEmpty()) {
            base = item.slug.trim();
        } else if (item.title != null && !item.title.trim().isEmpty()) {
            base = item.title.trim();
        } else if (item.projectId != null) {
            base = item.projectId.trim();
        }

        return normalizeContentKey(base);
    }

    private String normalizeContentKey(String text) {
        if (text == null) {
            return "";
        }

        String key = text.toLowerCase();

        key = key.replaceAll("\\(.*?\\)", "");
        key = key.replaceAll("\\[.*?\\]", "");

        key = key.replace("fabric", "");
        key = key.replace("forge", "");
        key = key.replace("quilt", "");
        key = key.replace("neoforge", "");

        key = key.replaceAll("[^a-z0-9]+", "-");
        key = key.replaceAll("-+", "-");
        key = key.replaceAll("^-|-$", "");

        return key;
    }

    private File getLogicalContentMarkerFile(String logicalKey, String type) {
        String safeKey = logicalKey == null ? "unknown" : logicalKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeType = type == null ? "mod" : type.replaceAll("[^a-zA-Z0-9._-]", "_");

        return new File(getContentRegistryDirectory(), "logical-" + safeType + "-" + safeKey + ".txt");
    }



    private boolean isLogicalContentInstalled(ModrinthClient.ModResult item) {
        if (item == null) {
            return false;
        }

        String logicalKey = getContentLogicalKey(item);

        if (logicalKey == null || logicalKey.trim().isEmpty()) {
            return false;
        }

        File marker = getLogicalContentMarkerFile(logicalKey, item.projectType);

        if (!marker.exists()) {
            return false;
        }

        File installedFile = readFileFromMarker(marker);

        if (installedFile != null && installedFile.exists()) {
            return true;
        }

        marker.delete();
        return false;
    }

    private void markLogicalContentInstalled(ModrinthClient.ModResult item, File file) {
        if (item == null || file == null) {
            return;
        }

        String logicalKey = getContentLogicalKey(item);

        if (logicalKey == null || logicalKey.trim().isEmpty()) {
            return;
        }

        File marker = getLogicalContentMarkerFile(logicalKey, item.projectType);

        PrintWriter writer = null;

        try {
            writer = new PrintWriter(new FileWriter(marker, false));
            writer.println("logicalKey=" + logicalKey);
            writer.println("title=" + (item.title == null ? "" : item.title));
            writer.println("slug=" + (item.slug == null ? "" : item.slug));
            writer.println("projectId=" + (item.projectId == null ? "" : item.projectId));
            writer.println("type=" + (item.projectType == null ? "mod" : item.projectType));
            writer.println("provider=" + getProviderLabelForResult(item));
            writer.println("iconUrl=" + (item.iconUrl == null ? "" : item.iconUrl));
            writer.println("file=" + file.getAbsolutePath());
            writer.println("installedAt=" + java.time.LocalDateTime.now());
        } catch (Exception ex) {
            System.err.println("No se pudo guardar marcador lógico: " + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private File readFileFromMarker(File marker) {
        if (marker == null || !marker.exists()) {
            return null;
        }

        java.io.BufferedReader reader = null;

        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(marker));

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("file=")) {
                    String path = line.substring("file=".length()).trim();

                    if (!path.isEmpty()) {
                        return new File(path);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private File installCurseForgeContentWithDependencies(ModrinthClient.ModResult selected,
                                                          String mcVersion,
                                                          Label statusLabel,
                                                          ProgressBar progressBar) throws Exception {
        java.util.Set<String> installing = new java.util.HashSet<String>();
        return installCurseForgeContentRecursive(selected, mcVersion, statusLabel, progressBar, installing, 0);
    }

    private File installCurseForgeContentRecursive(ModrinthClient.ModResult selected,
                                                   String mcVersion,
                                                   Label statusLabel,
                                                   ProgressBar progressBar,
                                                   java.util.Set<String> installing,
                                                   int depth) throws Exception {
        if (selected == null) {
            throw new Exception("No hay contenido seleccionado.");
        }

        if (depth > 10) {
            throw new Exception("Demasiadas dependencias anidadas.");
        }

        String apiKey = getCurseForgeApiKey();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("CurseForge API Key no configurada.");
        }

        String type = selected.projectType == null || selected.projectType.trim().isEmpty()
                ? "mod"
                : selected.projectType;

        String projectId = selected.projectId;

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new Exception("Project ID inválido.");
        }

        if (isContentInstalled(projectId, type)) {
            File existing = findInstalledContentFile(projectId, type);

            if (existing != null && existing.exists()) {
                return existing;
            }
        }

        if (installing.contains(projectId)) {
            throw new Exception("Dependencia circular detectada: " + projectId);
        }

        installing.add(projectId);

        final String resolving = "Resolviendo " + selected.title + " desde CurseForge...";
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(resolving);
            }
        });

        ModrinthClient.ModVersionFile fileData = CurseForgeClient.getLatestVersionFile(
                apiKey,
                projectId,
                mcVersion,
                type
        );

        if (fileData.dependencyProjectIds != null && !fileData.dependencyProjectIds.isEmpty()) {
            for (String depProjectId : fileData.dependencyProjectIds) {
                if (depProjectId == null || depProjectId.trim().isEmpty()) {
                    continue;
                }

                if (isContentInstalled(depProjectId, "mod")) {
                    continue;
                }

                ModrinthClient.ModResult depResult = new ModrinthClient.ModResult(
                        depProjectId,
                        depProjectId,
                        "Dependencia requerida de CurseForge",
                        depProjectId,
                        "mod"
                );

                final String depStatus = "Instalando dependencia CurseForge: " + depProjectId;

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(depStatus);
                    }
                });

                installCurseForgeContentRecursive(depResult, mcVersion, statusLabel, progressBar, installing, depth + 1);
            }
        }

        final String downloading = "Descargando " + selected.title + " desde CurseForge...";

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(downloading);
            }
        });

        File targetDir = getContentDirectory(type);
        targetDir.mkdirs();

        String safeName = safeModFileName(fileData.filename);
        File targetFile = new File(targetDir, safeName);

        if (targetFile.exists() && targetFile.length() > 0) {
            markContentInstalled(projectId, type, selected.title, targetFile);
            installing.remove(projectId);
            return targetFile;
        }

        downloadFileWithProgress(
                fileData.url,
                targetFile,
                statusLabel,
                progressBar,
                selected.title
        );

        markContentInstalled(projectId, type, selected.title, targetFile);
        markLogicalContentInstalled(selected, targetFile);

        installing.remove(projectId);

        return targetFile;
    }

    private void downloadFileWithProgress(final String urlStr,
                                          final File targetFile,
                                          final Label statusLabel,
                                          final ProgressBar progressBar,
                                          final String displayName) throws Exception {
        targetFile.getParentFile().mkdirs();

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;

        File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");

        try {
            java.net.URL url = java.net.URI.create(urlStr).toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 MinecraftLauncher/1.0");
            conn.setRequestProperty("Accept", "*/*");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                String body = "";

                try {
                    java.io.InputStream err = conn.getErrorStream();

                    if (err != null) {
                        java.util.Scanner scanner = new java.util.Scanner(err, "UTF-8").useDelimiter("\\A");
                        body = scanner.hasNext() ? scanner.next() : "";
                        scanner.close();
                        err.close();
                    }
                } catch (Exception ignored) {
                }

                throw new Exception("HTTP " + code + " al descargar archivo. " + body);
            }

            final long totalBytes = conn.getContentLengthLong();

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (progressBar != null) {
                        progressBar.setVisible(true);

                        if (totalBytes > 0) {
                            progressBar.setProgress(0);
                        } else {
                            progressBar.setProgress(-1);
                        }
                    }

                    if (statusLabel != null) {
                        statusLabel.setText("Descargando " + displayName + "...");
                    }
                }
            });

            in = conn.getInputStream();
            out = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;
            long lastUiUpdate = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;

                long now = System.currentTimeMillis();

                if (now - lastUiUpdate > 120 || downloaded == totalBytes) {
                    lastUiUpdate = now;

                    final long currentDownloaded = downloaded;

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            if (statusLabel != null) {
                                if (totalBytes > 0) {
                                    int percent = (int) ((currentDownloaded * 100) / totalBytes);

                                    statusLabel.setText(
                                            "Descargando " + displayName + "... " +
                                                    percent + "% · " +
                                                    formatBytes(currentDownloaded) + " / " +
                                                    formatBytes(totalBytes)
                                    );
                                } else {
                                    statusLabel.setText(
                                            "Descargando " + displayName + "... " +
                                                    formatBytes(currentDownloaded)
                                    );
                                }
                            }

                            if (progressBar != null && totalBytes > 0) {
                                progressBar.setProgress(currentDownloaded / (double) totalBytes);
                            }
                        }
                    });
                }
            }

            out.close();
            out = null;

            if (tempFile.length() == 0) {
                throw new Exception("La descarga quedó vacía.");
            }

            if (targetFile.exists()) {
                targetFile.delete();
            }

            if (!tempFile.renameTo(targetFile)) {
                throw new Exception("No se pudo mover el archivo descargado a su destino.");
            }

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (progressBar != null) {
                        progressBar.setProgress(1);
                    }

                    if (statusLabel != null) {
                        statusLabel.setText("Descarga completada: " + targetFile.getName());
                    }
                }
            });
        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }

            if (tempFile.exists() && (!targetFile.exists() || targetFile.length() == 0)) {
                tempFile.delete();
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb);
        }

        double mb = kb / 1024.0;

        if (mb < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", mb);
        }

        double gb = mb / 1024.0;

        return String.format(java.util.Locale.US, "%.2f GB", gb);
    }

    private File installContentFromModrinth(ModrinthClient.ModResult selected, String mcVersion) throws Exception {
        if (selected == null) {
            throw new Exception("No hay contenido seleccionado.");
        }

        String type = selected.projectType;

        if (type == null || type.trim().isEmpty()) {
            type = "mod";
        }

        if (isContentInstalled(selected.projectId, type)) {
            File existing = findInstalledContentFile(selected.projectId, type);

            if (existing != null && existing.exists()) {
                throw new Exception("Este contenido ya está instalado: " + existing.getName());
            }

            throw new Exception("Este contenido ya está marcado como instalado.");
        }

        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                selected.projectId,
                mcVersion,
                type
        );

        File targetDir = getContentDirectory(type);
        targetDir.mkdirs();

        String safeName = safeModFileName(fileData.filename);
        File targetFile = new File(targetDir, safeName);

        if (targetFile.exists() && targetFile.length() > 0) {
            markContentInstalled(selected.projectId, type, selected.title, targetFile);
            throw new Exception("Este contenido ya está instalado: " + targetFile.getName());
        }

        downloadModFile(fileData.url, targetFile);

        markContentInstalled(selected.projectId, type, selected.title, targetFile);

        return targetFile;
    }

    private String getSelectedContentType(ComboBox<String> typeBox) {
        String value = typeBox.getValue();

        if ("Texturas".equals(value)) {
            return "resourcepack";
        }

        if ("Shaders".equals(value)) {
            return "shader";
        }

        if ("Modpacks".equals(value)) {
            return "modpack";
        }

        return "mod";
    }

    private String getContentTypeLabel(String type) {
        if ("resourcepack".equalsIgnoreCase(type)) {
            return "Textura";
        }

        if ("shader".equalsIgnoreCase(type)) {
            return "Shader";
        }

        if ("modpack".equalsIgnoreCase(type)) {
            return "Modpack";
        }

        return "Mod";
    }

    private File getContentDirectory(String type) {
        File gameDir = getCurrentInstanceGameDir();

        if ("resourcepack".equalsIgnoreCase(type)) {
            return new File(gameDir, "resourcepacks");
        }

        if ("shader".equalsIgnoreCase(type)) {
            return new File(gameDir, "shaderpacks");
        }

        return new File(gameDir, "mods");
    }

    private void updateContentInstallButtonState(ModrinthClient.ModResult selected, Button installBtn, Label statusLabel) {
        if (selected == null) {
            installBtn.setText("Instalar seleccionado");
            installBtn.setDisable(true);
            return;
        }

        if (isContentInstalled(selected.projectId, selected.projectType) || isLogicalContentInstalled(selected)) {
            installBtn.setText("Ya instalado");
            installBtn.setDisable(true);

            File installed = findInstalledContentFile(selected.projectId, selected.projectType);

            if (installed != null && installed.exists()) {
                statusLabel.setText("Ya instalado: " + installed.getName());
            } else {
                statusLabel.setText("Este contenido o su equivalente ya estaba instalado.");
            }

            return;
        }

        installBtn.setText("Instalar seleccionado");
        installBtn.setDisable(false);
        statusLabel.setText("Seleccionado: " + selected.title);
    }

    private void showCosmeticsDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Cosméticos");
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("cosmetics-root");

        Label title = new Label("Cosméticos");
        title.getStyleClass().add("namemc-title");

        Label subtitle = new Label("Selecciona una skin y una capa para previsualizarlas en 3D.");
        subtitle.getStyleClass().add("muted-label");
        subtitle.setWrapText(true);

        VBox skinCard = createCosmeticCard("Skin", "Formato recomendado: skin clásica o slim en PNG.");
        VBox capeCard = createCosmeticCard("Capa", "Formato recomendado: capa Minecraft 64x32 o 64x64 en PNG.");

        final Label skinPathLabel = new Label(getSkinLabelText());
        skinPathLabel.getStyleClass().add("path-label");
        skinPathLabel.setWrapText(true);

        final Label capePathLabel = new Label(getCapeLabelText());
        capePathLabel.getStyleClass().add("path-label");
        capePathLabel.setWrapText(true);

        Button chooseSkinBtn = new Button("Elegir Skin");
        chooseSkinBtn.getStyleClass().add("button");

        Button removeSkinBtn = new Button("Quitar Skin");
        removeSkinBtn.getStyleClass().add("secondary-button");

        Button chooseCapeBtn = new Button("Elegir Capa");
        chooseCapeBtn.getStyleClass().add("button");

        Button removeCapeBtn = new Button("Quitar Capa");
        removeCapeBtn.getStyleClass().add("secondary-button");

        Button viewerBtn = new Button("Abrir Visor 3D");
        viewerBtn.getStyleClass().add("launch-button");
        viewerBtn.setMaxWidth(Double.MAX_VALUE);

        Button openSkinsFolderBtn = new Button("Abrir carpeta de skins");
        openSkinsFolderBtn.getStyleClass().add("secondary-button");
        openSkinsFolderBtn.setMaxWidth(Double.MAX_VALUE);

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setMaxWidth(Double.MAX_VALUE);

        chooseSkinBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File selected = choosePngFile(dialog, "Elegir skin PNG");

                if (selected != null) {
                    selectedSkinFile = selected;
                    prefs.put("customSkinFile", selectedSkinFile.getAbsolutePath());
                    skinPathLabel.setText(getSkinLabelText());
                    updateAvatar(usernameField.getText());
                }
            }
        });

        removeSkinBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                selectedSkinFile = null;
                prefs.remove("customSkinFile");
                skinPathLabel.setText(getSkinLabelText());
                updateAvatar(usernameField.getText());
            }
        });

        chooseCapeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File selected = choosePngFile(dialog, "Elegir capa PNG");

                if (selected != null) {
                    selectedCapeFile = selected;
                    prefs.put("customCapeFile", selectedCapeFile.getAbsolutePath());
                    capePathLabel.setText(getCapeLabelText());
                }
            }
        });

        removeCapeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                selectedCapeFile = null;
                prefs.remove("customCapeFile");
                capePathLabel.setText(getCapeLabelText());
            }
        });

        viewerBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                show3DSkinViewer();
            }
        });

        openSkinsFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File skinsDir = new File(VersionManager.MC_DIR, "skins");
                    skinsDir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(skinsDir);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        HBox skinButtons = new HBox(10, chooseSkinBtn, removeSkinBtn);
        HBox capeButtons = new HBox(10, chooseCapeBtn, removeCapeBtn);

        skinCard.getChildren().addAll(skinPathLabel, skinButtons);
        capeCard.getChildren().addAll(capePathLabel, capeButtons);

        root.getChildren().addAll(
                title,
                subtitle,
                skinCard,
                capeCard,
                viewerBtn,
                openSkinsFolderBtn,
                closeBtn
        );

        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        Scene scene = new Scene(root, 520, 560);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.show();
    }

    private VBox createCosmeticCard(String titleText, String descriptionText) {
        VBox card = new VBox(10);
        card.getStyleClass().add("namemc-card");

        Label title = new Label(titleText);
        title.getStyleClass().add("card-title");

        Label description = new Label(descriptionText);
        description.getStyleClass().add("muted-label");
        description.setWrapText(true);

        card.getChildren().addAll(title, description);

        return card;
    }

    private File choosePngFile(Stage owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagen PNG", "*.png")
        );

        File skinsDir = new File(VersionManager.MC_DIR, "skins");

        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
        }

        chooser.setInitialDirectory(skinsDir);

        return chooser.showOpenDialog(owner);
    }

    private String getSkinLabelText() {
        if (selectedSkinFile != null && selectedSkinFile.exists()) {
            return selectedSkinFile.getName();
        }

        return "Usando skin por nombre de usuario.";
    }

    private String getCapeLabelText() {
        if (selectedCapeFile != null && selectedCapeFile.exists()) {
            return selectedCapeFile.getName();
        }

        return "Sin capa seleccionada.";
    }

    private String buildSkinViewerHtml(String skinSource, String capeSource) {
        String skinJs = toJsString(skinSource);
        String capeJs = capeSource == null ? "null" : toJsString(capeSource);

        return ""
                + "<!DOCTYPE html>"
                + "<html lang='es'>"
                + "<head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Visor 3D de Skin</title>"
                + "<style>"
                + ":root{"
                + "--bg:#f6f8fb;"
                + "--card:#ffffff;"
                + "--text:#111827;"
                + "--muted:#6b7280;"
                + "--border:#e5e7eb;"
                + "--blue:#2563eb;"
                + "}"
                + "*{box-sizing:border-box;}"
                + "html,body{"
                + "margin:0;"
                + "width:100%;"
                + "height:100%;"
                + "background:var(--bg);"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,Arial,sans-serif;"
                + "color:var(--text);"
                + "overflow:hidden;"
                + "}"
                + ".page{"
                + "width:100%;"
                + "height:100%;"
                + "display:flex;"
                + "align-items:center;"
                + "justify-content:center;"
                + "padding:28px;"
                + "}"
                + ".card{"
                + "width:460px;"
                + "max-width:100%;"
                + "background:var(--card);"
                + "border:1px solid var(--border);"
                + "border-radius:24px;"
                + "box-shadow:0 20px 50px rgba(15,23,42,.08);"
                + "padding:18px;"
                + "}"
                + ".head{"
                + "display:flex;"
                + "align-items:center;"
                + "justify-content:space-between;"
                + "gap:12px;"
                + "margin-bottom:14px;"
                + "}"
                + ".title{"
                + "font-size:20px;"
                + "font-weight:800;"
                + "letter-spacing:-.02em;"
                + "}"
                + ".badge{"
                + "font-size:12px;"
                + "font-weight:700;"
                + "color:#1d4ed8;"
                + "background:#dbeafe;"
                + "padding:6px 10px;"
                + "border-radius:999px;"
                + "}"
                + ".viewer-wrap{"
                + "position:relative;"
                + "width:100%;"
                + "height:520px;"
                + "border-radius:18px;"
                + "overflow:hidden;"
                + "background:linear-gradient(180deg,#eef2ff 0%,#f8fafc 100%);"
                + "border:1px solid #eef2f7;"
                + "}"
                + "canvas{"
                + "display:block;"
                + "width:100%;"
                + "height:100%;"
                + "}"
                + ".footer{"
                + "display:flex;"
                + "align-items:center;"
                + "justify-content:space-between;"
                + "gap:12px;"
                + "margin-top:12px;"
                + "font-size:12px;"
                + "color:var(--muted);"
                + "}"
                + ".hint{"
                + "font-size:12px;"
                + "color:var(--muted);"
                + "}"
                + ".error{"
                + "display:none;"
                + "position:absolute;"
                + "left:16px;"
                + "right:16px;"
                + "bottom:16px;"
                + "background:#fef2f2;"
                + "border:1px solid #fecaca;"
                + "color:#991b1b;"
                + "border-radius:14px;"
                + "padding:12px;"
                + "font-size:13px;"
                + "line-height:1.45;"
                + "}"
                + ".loading{"
                + "position:absolute;"
                + "inset:0;"
                + "display:flex;"
                + "align-items:center;"
                + "justify-content:center;"
                + "font-size:13px;"
                + "font-weight:700;"
                + "color:#64748b;"
                + "}"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='page'>"
                + "<div class='card'>"
                + "<div class='head'>"
                + "<div>"
                + "<div class='title'>Visor 3D de Skin</div>"
                + "<div class='hint'>Arrastra para rotar · rueda para zoom</div>"
                + "</div>"
                + "<div class='badge'>WebGL</div>"
                + "</div>"
                + "<div class='viewer-wrap'>"
                + "<div id='loading' class='loading'>Cargando visor 3D...</div>"
                + "<canvas id='skin_container'></canvas>"
                + "<div id='error' class='error'></div>"
                + "</div>"
                + "<div class='footer'>"
                + "<span>Launcher skin preview</span>"
                + "<span>Powered by skinview3d</span>"
                + "</div>"
                + "</div>"
                + "</div>"
                + "<script src='https://unpkg.com/skinview3d@3.0.1/bundles/skinview3d.bundle.js'></script>"
                + "<script>"
                + "const skinSrc=" + skinJs + ";"
                + "const capeSrc=" + capeJs + ";"
                + "const errorBox=document.getElementById('error');"
                + "const loading=document.getElementById('loading');"
                + "function showError(msg){"
                + "loading.style.display='none';"
                + "errorBox.style.display='block';"
                + "errorBox.textContent=msg;"
                + "}"
                + "function hasWebGL(){"
                + "try{"
                + "const c=document.createElement('canvas');"
                + "return !!(window.WebGLRenderingContext&&(c.getContext('webgl')||c.getContext('experimental-webgl')));"
                + "}catch(e){return false;}"
                + "}"
                + "window.addEventListener('load',function(){"
                + "try{"
                + "if(!hasWebGL()){"
                + "showError('Tu navegador no tiene WebGL activo. Activa aceleración por hardware o prueba Chrome/Edge/Firefox.');"
                + "return;"
                + "}"
                + "if(typeof skinview3d==='undefined'){"
                + "showError('No se pudo cargar skinview3d. Revisa tu conexión a internet.');"
                + "return;"
                + "}"
                + "const canvas=document.getElementById('skin_container');"
                + "const viewer=new skinview3d.SkinViewer({"
                + "canvas:canvas,"
                + "width:430,"
                + "height:520,"
                + "skin:skinSrc"
                + "});"
                + "viewer.camera.position.x=26;"
                + "viewer.camera.position.y=18;"
                + "viewer.camera.position.z=42;"
                + "viewer.fov=45;"
                + "viewer.zoom=0.92;"
                + "viewer.globalLight.intensity=0.9;"
                + "viewer.cameraLight.intensity=0.75;"
                + "viewer.animation=new skinview3d.IdleAnimation();"
                + "viewer.animation.speed=0.65;"
                + "if(capeSrc){viewer.loadCape(capeSrc);}"
                + "if(viewer.controls){"
                + "viewer.controls.enableRotate=true;"
                + "viewer.controls.enableZoom=true;"
                + "viewer.controls.enablePan=false;"
                + "}"
                + "loading.style.display='none';"
                + "}catch(e){"
                + "showError('No se pudo cargar el visor 3D: '+e.message);"
                + "}"
                + "});"
                + "</script>"
                + "</body>"
                + "</html>";
    }

    private String fileToDataUrl(File file, String mime) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mime + ";base64," + encoded;
    }

    private String toJsString(String value) {
        if (value == null) {
            return "null";
        }

        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        return "'" + escaped + "'";
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private void updateAvatar(String username) {
        try {
            if (selectedSkinFile != null && selectedSkinFile.exists()) {
                Image img = new Image(selectedSkinFile.toURI().toString(), true);
                avatarView.setImage(img);
                return;
            }

            String name = username == null ? "" : username.trim();

            if (name.isEmpty()) {
                name = "MHF_Steve";
            }

            Image img = new Image("https://minotar.net/helm/" + urlEncode(name) + "/100.png", true);
            avatarView.setImage(img);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadContentIcon(final String iconUrl,
                                 final String type,
                                 final StackPane iconBox) {
        ModrinthClient.ModResult temp = new ModrinthClient.ModResult(
                "",
                "",
                "",
                "",
                type == null ? "mod" : type
        );

        temp.iconUrl = iconUrl;

        loadContentIcon(temp, iconBox);
    }

    private void loadContentIcon(final ModrinthClient.ModResult item,
                                 final StackPane iconBox) {
        iconBox.getChildren().clear();

        String type = item == null ? "mod" : item.projectType;

        Label fallbackIcon = new Label(getContentFallbackIcon(type));
        fallbackIcon.getStyleClass().add("content-icon-fallback");
        iconBox.getChildren().add(fallbackIcon);

        if (item == null) {
            return;
        }

        final String iconKey = getIconCacheKey(item);
        iconBox.getProperties().put("iconKey", iconKey);

        Image cached = iconMemoryCache.get(iconKey);

        if (cached != null && !cached.isError()) {
            if (!iconKey.equals(iconBox.getProperties().get("iconKey"))) {
                return;
            }

            iconBox.getChildren().clear();
            iconBox.getChildren().add(createCachedIconView(cached));
            return;
        }

        final File cachedFile = getIconCacheFile(item);

        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                Image localImage = new Image(cachedFile.toURI().toString(), 54, 54, true, true, false);

                if (!localImage.isError()) {
                    iconMemoryCache.put(iconKey, localImage);

                    if (!iconKey.equals(iconBox.getProperties().get("iconKey"))) {
                        return;
                    }

                    iconBox.getChildren().clear();
                    iconBox.getChildren().add(createCachedIconView(localImage));
                    return;
                } else {
                    cachedFile.delete();
                }
            } catch (Exception ignored) {
                cachedFile.delete();
            }
        }

        iconExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    java.util.List<String> candidates = getIconUrlCandidates(item);

                    Exception lastError = null;
                    boolean downloaded = false;

                    for (String url : candidates) {
                        try {
                            downloadAndConvertIconToPng(url, cachedFile);
                            downloaded = true;
                            break;
                        } catch (Exception ex) {
                            lastError = ex;
                            System.err.println("[IconCache] Falló candidato: " + url + " | " + ex.getMessage());
                        }
                    }

                    if (!downloaded) {
                        if (lastError != null) {
                            throw lastError;
                        }

                        throw new Exception("No hay URLs candidatas para icono.");
                    }

                    final Image image = new Image(cachedFile.toURI().toString(), 54, 54, true, true, false);

                    if (!image.isError()) {
                        iconMemoryCache.put(iconKey, image);

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                if (!iconKey.equals(iconBox.getProperties().get("iconKey"))) {
                                    return;
                                }

                                iconBox.getChildren().clear();
                                iconBox.getChildren().add(createCachedIconView(image));
                            }
                        });
                    } else {
                        cachedFile.delete();
                    }
                } catch (Exception ex) {
                    System.err.println("[IconCache] No se pudo cargar icono de "
                            + item.title + " | projectId=" + item.projectId + " | " + ex.getMessage());
                }
            }
        });
    }

    private java.util.List<String> getIconUrlCandidates(ModrinthClient.ModResult item) {
        java.util.List<String> urls = new ArrayList<String>();

        if (item == null) {
            return urls;
        }

        addIconUrlCandidate(urls, item.iconUrl);

        if (item.projectId != null && !item.projectId.trim().isEmpty()) {
            String id = item.projectId.trim();

            addIconUrlCandidate(urls, "https://cdn.modrinth.com/data/" + id + "/icon.png");
            addIconUrlCandidate(urls, "https://cdn.modrinth.com/data/" + id + "/icon.jpg");
            addIconUrlCandidate(urls, "https://cdn.modrinth.com/data/" + id + "/icon.jpeg");
            addIconUrlCandidate(urls, "https://cdn.modrinth.com/data/" + id + "/icon.gif");
        }

        return urls;
    }

    private void addIconUrlCandidate(java.util.List<String> urls, String url) {
        if (url == null) {
            return;
        }

        String clean = url.trim();

        if (clean.isEmpty()) {
            return;
        }

        String lower = clean.toLowerCase();

        if (lower.contains(".webp") || lower.contains(".svg")) {
            String proxied = createPngProxyUrl(clean);

            if (proxied != null && !urls.contains(proxied)) {
                urls.add(proxied);
            }

            return;
        }

        if (!urls.contains(clean)) {
            urls.add(clean);
        }
    }

    private String createPngProxyUrl(String originalUrl) {
        try {
            String clean = originalUrl.trim();

            if (clean.startsWith("https://")) {
                clean = clean.substring("https://".length());
            } else if (clean.startsWith("http://")) {
                clean = clean.substring("http://".length());
            }

            String encoded = URLEncoder.encode(clean, "UTF-8");

            return "https://wsrv.nl/?url=" + encoded + "&output=png&w=96&h=96&fit=cover";
        } catch (Exception ex) {
            return null;
        }
    }

    private String getIconCacheKey(ModrinthClient.ModResult item) {
        if (item == null) {
            return "unknown";
        }

        String type = item.projectType == null ? "mod" : item.projectType;
        String id = item.projectId == null ? "" : item.projectId;

        if (!id.trim().isEmpty()) {
            return type + "-" + id;
        }

        String slug = item.slug == null ? "" : item.slug;

        if (!slug.trim().isEmpty()) {
            return type + "-" + slug;
        }

        String icon = item.iconUrl == null ? "" : item.iconUrl;

        return type + "-" + icon;
    }

    private File getIconCacheFile(ModrinthClient.ModResult item) {
        String key = getIconCacheKey(item);
        return new File(getIconCacheDirectory(), sha1(key) + ".png");
    }

    private ImageView createCachedIconView(Image image) {
        ImageView iconView = new ImageView(image);
        iconView.setFitWidth(54);
        iconView.setFitHeight(54);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
        iconView.getStyleClass().add("content-icon-image");
        return iconView;
    }

    private File getIconCacheDirectory() {
        File dir = new File(PlatformManager.getLauncherDataDir(), "icon-cache");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private File getIconCacheFile(String url) {
        String hash = sha1(url);
        return new File(getIconCacheDirectory(), hash + ".png");
    }

    private void downloadAndConvertIconToPng(String urlStr, File targetFile) throws Exception {
        if (targetFile.exists() && targetFile.length() > 0) {
            return;
        }

        File parent = targetFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;

        try {
            java.net.URL url = java.net.URI.create(urlStr).toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ProfessionalMinecraftLauncher/1.0");
            conn.setRequestProperty("Accept", "image/png,image/jpeg,image/jpg,image/gif,*/*");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }

            String contentType = conn.getContentType();

            if (contentType != null) {
                String lowerType = contentType.toLowerCase();

                if (lowerType.contains("webp") || lowerType.contains("svg")) {
                    throw new Exception("Formato no soportado sin librería externa: " + contentType);
                }
            }

            in = conn.getInputStream();

            BufferedImage original = ImageIO.read(in);

            if (original == null) {
                throw new Exception("ImageIO no pudo leer la imagen.");
            }

            BufferedImage converted = new BufferedImage(
                    original.getWidth(),
                    original.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            java.awt.Graphics2D g = converted.createGraphics();

            try {
                g.drawImage(original, 0, 0, null);
            } finally {
                g.dispose();
            }

            boolean written = ImageIO.write(converted, "png", tempFile);

            if (!written || tempFile.length() == 0) {
                throw new Exception("No se pudo convertir icono a PNG.");
            }

            if (targetFile.exists()) {
                targetFile.delete();
            }

            if (!tempFile.renameTo(targetFile)) {
                throw new Exception("No se pudo guardar icono en caché.");
            }
        } finally {
            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }

            if (tempFile.exists() && (!targetFile.exists() || targetFile.length() == 0)) {
                tempFile.delete();
            }
        }
    }

    private String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();

            for (byte b : hash) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }

            return sb.toString();
        } catch (Exception ex) {
            return String.valueOf(Math.abs(text.hashCode()));
        }
    }

    private void show3DSkinViewer() {
        try {
            SkinViewer3D.show(
                    usernameField.getScene() == null ? null : usernameField.getScene().getWindow(),
                    usernameField.getText(),
                    selectedSkinFile,
                    selectedCapeFile
            );
        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo abrir el visor 3D");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private File createSkinViewerHtmlFile(String skinSource, String capeSource) throws Exception {
        String html = buildSkinViewerHtml(skinSource, capeSource);

        File dir = new File(System.getProperty("java.io.tmpdir"), "minecraft-launcher-viewer");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        File htmlFile = new File(dir, "skin-viewer.html");

        java.nio.file.Files.write(
                htmlFile.toPath(),
                html.getBytes(StandardCharsets.UTF_8)
        );

        return htmlFile;
    }

    private void showGraphicsPackDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Pack Gráfico");
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e2e;");

        Label title = new Label("Instalar Pack Gráfico");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #f9e2af;");

        String selectedVersion = "ninguna";
        if (versionBox.getValue() != null) {
            selectedVersion = versionBox.getValue().id;
        }

        Label info = new Label(
                "Versión seleccionada: " + selectedVersion + "\n\n" +
                        "Este pack instalará mods recomendados para mejorar gráficos y rendimiento:\n" +
                        "• Sodium\n" +
                        "• Iris Shaders\n" +
                        "• Sodium Extra\n" +
                        "• Reese's Sodium Options\n" +
                        "• Indium\n" +
                        "• Entity Model Features\n" +
                        "• Entity Texture Features\n" +
                        "• Continuity\n\n" +
                        "Después podrás activar shaders dentro del juego."
        );
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13px;");

        final CheckBox installShaderCheck = new CheckBox("Instalar shaderpack Complementary Reimagined");
        installShaderCheck.setSelected(true);
        installShaderCheck.setStyle("-fx-text-fill: #cdd6f4;");

        final Label statusLbl = new Label("Listo para instalar.");
        statusLbl.setWrapText(true);
        statusLbl.setStyle("-fx-text-fill: #a6adc8;");

        final ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setVisible(false);

        Button installBtn = new Button("Instalar Pack Gráfico");
        installBtn.getStyleClass().add("launch-button");
        installBtn.setMaxWidth(Double.MAX_VALUE);

        Button openShaderFolderBtn = new Button("Abrir carpeta shaderpacks");
        openShaderFolderBtn.getStyleClass().add("secondary-button");
        openShaderFolderBtn.setMaxWidth(Double.MAX_VALUE);

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setMaxWidth(Double.MAX_VALUE);

        installBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                installBtn.setDisable(true);
                bar.setVisible(true);
                bar.setProgress(-1);
                installGraphicsPack(statusLbl, installBtn, bar, installShaderCheck.isSelected());
            }
        });

        openShaderFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File shaderpacksDir = new File(getCurrentInstanceGameDir(), "shaderpacks");
                    shaderpacksDir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(shaderpacksDir);
                    }
                } catch (Exception ex) {
                    statusLbl.setText("No se pudo abrir la carpeta shaderpacks: " + ex.getMessage());
                }
            }
        });

        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        root.getChildren().addAll(
                title,
                info,
                installShaderCheck,
                statusLbl,
                bar,
                installBtn,
                openShaderFolderBtn,
                closeBtn
        );

        Scene scene = new Scene(root, 520, 560);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ex) {
        }

        dialog.setScene(scene);
        dialog.show();
    }

    private void installGraphicsPack(final Label statusLbl, final Button installBtn, final ProgressBar bar,
                                     final boolean installShader) {
        final VersionEntry selected = versionBox.getValue();

        if (selected == null) {
            statusLbl.setText("Selecciona primero una versión de Minecraft/Fabric.");
            installBtn.setDisable(false);
            bar.setVisible(false);
            return;
        }

        final String mcVersion = extractMinecraftVersionForMods(selected.id);

        new Thread(new Runnable() {
            @Override
            public void run() {
                int installed = 0;
                int skipped = 0;
                int failed = 0;

                final StringBuilder errors = new StringBuilder();

                try {
                    File modsDir = new File(getCurrentInstanceGameDir(), "mods");
                    modsDir.mkdirs();

                    File shaderpacksDir = new File(VersionManager.MC_DIR, "shaderpacks");
                    shaderpacksDir.mkdirs();

                    String[][] mods = new String[][]{
                            {"sodium", "Sodium"},
                            {"iris", "Iris Shaders"},
                            {"sodium-extra", "Sodium Extra"},
                            {"reeses-sodium-options", "Reese's Sodium Options"},
                            {"indium", "Indium"},
                            {"entity-model-features", "Entity Model Features"},
                            {"entitytexturefeatures", "Entity Texture Features"},
                            {"continuity", "Continuity"}
                    };

                    for (int i = 0; i < mods.length; i++) {
                        final String slug = mods[i][0];
                        final String displayName = mods[i][1];

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                statusLbl.setText("Instalando " + displayName + " para Minecraft " + mcVersion + "...");
                            }
                        });

                        try {
                            boolean didInstall = installModrinthProjectToFolder(slug, mcVersion, modsDir);

                            if (didInstall) {
                                installed++;
                            } else {
                                skipped++;
                            }
                        } catch (Exception ex) {
                            failed++;
                            errors.append("• ").append(displayName).append(": ").append(ex.getMessage()).append("\n");
                            System.err.println("[GraphicsPack] Error installing " + displayName + ": " + ex.getMessage());
                        }
                    }

                    if (installShader) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                statusLbl.setText("Instalando shaderpack Complementary Reimagined...");
                            }
                        });

                        try {
                            boolean didInstallShader = installModrinthProjectToFolder(
                                    "complementary-reimagined",
                                    mcVersion,
                                    shaderpacksDir
                            );

                            if (didInstallShader) {
                                installed++;
                            } else {
                                skipped++;
                            }
                        } catch (Exception ex) {
                            failed++;
                            errors.append("• Complementary Reimagined: ").append(ex.getMessage()).append("\n");
                            System.err.println("[GraphicsPack] Error installing shaderpack: " + ex.getMessage());
                        }
                    }

                    final int finalInstalled = installed;
                    final int finalSkipped = skipped;
                    final int finalFailed = failed;

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            String msg = "Pack gráfico terminado.\n\n" +
                                    "Instalados: " + finalInstalled + "\n" +
                                    "Ya existentes: " + finalSkipped + "\n" +
                                    "Fallidos: " + finalFailed + "\n\n";

                            if (finalFailed > 0) {
                                msg += "Errores:\n" + errors.toString() + "\n";
                            }

                            msg += "Ahora abre Minecraft, ve a Opciones > Video > Shader Packs y activa el shader.";

                            statusLbl.setText(msg);
                            installBtn.setDisable(false);
                            bar.setVisible(false);
                        }
                    });

                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLbl.setText("Error instalando pack gráfico: " + ex.getMessage());
                            installBtn.setDisable(false);
                            bar.setVisible(false);
                        }
                    });
                }
            }
        }, "Graphics-Pack-Installer").start();
    }

    private boolean installModrinthProjectToFolder(String slugOrProjectId, String mcVersion, File targetFolder) throws Exception {
        targetFolder.mkdirs();

        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(slugOrProjectId, mcVersion);

        String safeName = makeSafeFileName(fileData.filename);
        File targetFile = new File(targetFolder, safeName);

        if (targetFile.exists() && targetFile.length() > 0) {
            System.out.println("[GraphicsPack] Already installed: " + targetFile.getName());
            return false;
        }

        System.out.println("[GraphicsPack] Downloading " + fileData.url);
        downloadFile(fileData.url, targetFile);

        System.out.println("[GraphicsPack] Installed: " + targetFile.getAbsolutePath());
        return true;
    }

    private void downloadFile(String urlStr, File targetFile) throws Exception {
        targetFile.getParentFile().mkdirs();

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;

        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ProfessionalMinecraftLauncher/1.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " al descargar " + urlStr);
            }

            in = conn.getInputStream();
            out = new java.io.FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String makeSafeFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "downloaded-file.jar";
        }

        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String extractMinecraftVersionForMods(String versionId) {
        if (versionId == null) {
            return "";
        }

        versionId = versionId.trim();

        if (versionId.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return versionId;
        }

        String[] parts = versionId.split("-");

        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return parts[i];
            }
        }

        return versionId;
    }

    private VBox createToolCard(String titleText, String descriptionText, String iconText) {
        VBox card = new VBox(6);
        card.getStyleClass().add("tool-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(76);
        card.setCursor(javafx.scene.Cursor.HAND);

        Label icon = new Label(iconText);
        icon.getStyleClass().add("tool-card-icon");

        Label title = new Label(titleText);
        title.getStyleClass().add("tool-card-title");

        Label description = new Label(descriptionText);
        description.getStyleClass().add("tool-card-description");
        description.setWrapText(true);

        card.getChildren().addAll(icon, title, description);

        return card;
    }


    private File installModFromModrinth(ModrinthClient.ModResult selected, String mcVersion) throws Exception {
        if (selected == null) {
            throw new Exception("No hay ningún mod seleccionado.");
        }

        if (mcVersion == null || mcVersion.trim().isEmpty()) {
            throw new Exception("Selecciona una versión de Minecraft primero.");
        }

        if (isModrinthProjectInstalled(selected.projectId)) {
            File existing = findInstalledFileByProjectId(selected.projectId);

            if (existing != null && existing.exists()) {
                throw new Exception("Este mod ya está instalado: " + existing.getName());
            }

            throw new Exception("Este mod ya está marcado como instalado.");
        }

        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                selected.projectId,
                mcVersion
        );

        File modsDir = getModsDirectory();
        modsDir.mkdirs();

        String safeName = safeModFileName(fileData.filename);
        File targetFile = new File(modsDir, safeName);

        if (targetFile.exists() && targetFile.length() > 0) {
            markModrinthProjectInstalled(selected.projectId, selected.title, targetFile);
            throw new Exception("Este mod ya está instalado: " + targetFile.getName());
        }

        downloadModFile(fileData.url, targetFile);

        markModrinthProjectInstalled(selected.projectId, selected.title, targetFile);

        return targetFile;
    }

    private void refreshInstalledMods(ListView<File> installedList, Label statusLabel) {
        File modsDir = getModsDirectory();

        if (!modsDir.exists()) {
            modsDir.mkdirs();
        }

        installedList.getItems().clear();

        File[] files = modsDir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();
                return lower.endsWith(".jar") || lower.endsWith(".disabled");
            }
        });

        if (files == null || files.length == 0) {
            statusLabel.setText("No hay mods instalados.");
            return;
        }

        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        for (File file : files) {
            installedList.getItems().add(file);
        }

        statusLabel.setText("Mods instalados: " + files.length);
    }

    private File getModsDirectory() {
        return new File(getCurrentInstanceGameDir(), "mods");
    }

    private File getContentRegistryDirectory() {
        File gameDir = getCurrentInstanceGameDir();

        File dir = new File(gameDir, ".launcher-content");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private File getContentMarkerFile(String projectId, String type) {
        String safeId = projectId == null ? "unknown" : projectId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeType = type == null ? "mod" : type.replaceAll("[^a-zA-Z0-9._-]", "_");

        return new File(getContentRegistryDirectory(), safeType + "-" + safeId + ".txt");
    }

    private boolean isContentInstalled(String projectId, String type) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return false;
        }

        File marker = getContentMarkerFile(projectId, type);

        if (!marker.exists()) {
            return false;
        }

        File installedFile = findInstalledContentFile(projectId, type);

        if (installedFile == null || !installedFile.exists()) {
            marker.delete();
            return false;
        }

        File expectedDir = getContentDirectory(type);

        if (!isFileInsideDirectory(installedFile, expectedDir)) {
            marker.delete();
            return false;
        }

        return true;
    }

    private boolean isFileInsideDirectory(File file, File directory) {
        try {
            if (file == null || directory == null) {
                return false;
            }

            String filePath = file.getCanonicalPath();
            String dirPath = directory.getCanonicalPath();

            return filePath.startsWith(dirPath + File.separator);
        } catch (Exception ex) {
            return false;
        }
    }

    private File findInstalledContentFile(String projectId, String type) {
        File marker = getContentMarkerFile(projectId, type);

        if (!marker.exists()) {
            return null;
        }

        java.io.BufferedReader reader = null;

        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(marker));

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("file=")) {
                    String path = line.substring("file=".length()).trim();

                    if (!path.isEmpty()) {
                        return new File(path);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private void markContentInstalled(String projectId, String type, String title, File file) {
        if (projectId == null || projectId.trim().isEmpty() || file == null) {
            return;
        }

        File marker = getContentMarkerFile(projectId, type);

        PrintWriter writer = null;

        try {
            writer = new PrintWriter(new FileWriter(marker, false));
            writer.println("projectId=" + projectId);
            writer.println("type=" + type);
            writer.println("title=" + (title == null ? "" : title));
            if (projectId != null && projectId.toLowerCase().startsWith("curseforge:")) {
                writer.println("provider=CurseForge");
            } else if (projectId != null && !projectId.trim().isEmpty()) {
                writer.println("provider=Modrinth");
            } else {
                writer.println("provider=Local");
            }
            writer.println("file=" + file.getAbsolutePath());
            writer.println("installedAt=" + java.time.LocalDateTime.now());
        } catch (Exception ex) {
            System.err.println("No se pudo guardar registro del contenido: " + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void removeMarkersForContentFile(File file) {
        if (file == null) {
            return;
        }

        File registry = getContentRegistryDirectory();
        File[] markers = registry.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".txt");
            }
        });

        if (markers == null) {
            return;
        }

        String filePath = file.getAbsolutePath();

        for (File marker : markers) {
            java.io.BufferedReader reader = null;

            try {
                reader = new java.io.BufferedReader(new java.io.FileReader(marker));

                String line;
                boolean matches = false;

                while ((line = reader.readLine()) != null) {
                    if (line.equals("file=" + filePath)) {
                        matches = true;
                        break;
                    }
                }

                if (matches) {
                    marker.delete();
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /*
     * Compatibilidad con métodos anteriores de mods.
     */

    private boolean isModrinthProjectInstalled(String projectId) {
        return isContentInstalled(projectId, "mod");
    }

    private File findInstalledFileByProjectId(String projectId) {
        return findInstalledContentFile(projectId, "mod");
    }

    private void markModrinthProjectInstalled(String projectId, String title, File file) {
        markContentInstalled(projectId, "mod", title, file);
    }

    private void removeMarkersForModFile(File modFile) {
        removeMarkersForContentFile(modFile);
    }

    private void updateInstallButtonState(ModrinthClient.ModResult selected, Button installBtn, Label statusLabel) {
        updateContentInstallButtonState(selected, installBtn, statusLabel);
    }

    private String getCurrentMinecraftVersionForMods() {
        if (versionBox.getValue() == null) {
            return "";
        }

        return extractMinecraftVersionForMods(versionBox.getValue().id);
    }

    private void downloadModFile(String urlStr, File targetFile) throws Exception {
        targetFile.getParentFile().mkdirs();

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;

        try {
            java.net.URL url = java.net.URI.create(urlStr).toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ProfessionalMinecraftLauncher/1.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " al descargar el mod.");
            }

            in = conn.getInputStream();
            out = new java.io.FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String safeModFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "modrinth-mod.jar";
        }

        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb);
        }

        double mb = kb / 1024.0;

        if (mb < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", mb);
        }

        double gb = mb / 1024.0;

        return String.format(java.util.Locale.US, "%.2f GB", gb);
    }

    private void showFolderDialog(String title, String folderName, final String extension) {
        final Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #1e1e2e;");

        Label header = new Label(title);
        header.getStyleClass().add("title-label");
        header.setStyle("-fx-font-size: 22px;");

        ListView<String> listView = new ListView<String>();
        listView.getStyleClass().add("list-view");

        final File folder = new File(VersionManager.MC_DIR, folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(extension);
            }
        });

        if (files != null && files.length > 0) {
            for (File f : files) {
                listView.getItems().add(f.getName());
            }
        } else {
            listView.getItems().add("(No se encontraron archivos)");
            listView.setDisable(true);
        }

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button openFolderBtn = new Button("Abrir Carpeta en Windows");
        openFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        PlatformManager.openFolder(folder);
                    }
                } catch (Exception ex) {
                }
            }
        });

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        buttons.getChildren().addAll(openFolderBtn, closeBtn);

        vbox.getChildren().addAll(header, listView, buttons);
        VBox.setVgrow(listView, Priority.ALWAYS);

        Scene scene = new Scene(vbox, 400, 500);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
        }
        dialog.setScene(scene);
        dialog.show();
    }

    private void loadProfilesIntoCombo() {
        profiles = ProfileManager.loadProfiles();

        if (profiles == null) {
            profiles = new ArrayList<Profile>();
        }

        if (profiles.isEmpty()) {
            Profile defaultProfile = new Profile(
                    "Principal",
                    usernameField.getText() == null || usernameField.getText().trim().isEmpty()
                            ? "Steve"
                            : usernameField.getText().trim(),
                    (int) ramSlider.getValue(),
                    versionBox != null && versionBox.getValue() != null ? versionBox.getValue().id : "",
                    "vanilla",
                    selectedSkinFile != null ? selectedSkinFile.getAbsolutePath() : "",
                    selectedCapeFile != null ? selectedCapeFile.getAbsolutePath() : ""
            );

            profiles.add(defaultProfile);

            try {
                ProfileManager.saveProfiles(profiles);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        profileBox.getItems().setAll(profiles);

        String lastProfileName = prefs.get("lastProfileName", "");

        if (!lastProfileName.isEmpty()) {
            for (Profile p : profiles) {
                if (p.name != null && p.name.equals(lastProfileName)) {
                    profileBox.getSelectionModel().select(p);
                    return;
                }
            }
        }

        if (!profiles.isEmpty()) {
            profileBox.getSelectionModel().selectFirst();
        }
    }

    private void createNewProfile() {
        TextInputDialog dialog = new TextInputDialog("Nuevo perfil");
        dialog.setTitle("Crear perfil");
        dialog.setHeaderText("Nombre del nuevo perfil");
        dialog.setContentText("Nombre:");

        java.util.Optional<String> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return;
        }

        String name = result.get() == null ? "" : result.get().trim();

        if (name.isEmpty()) {
            statusLabel.setText("El nombre del perfil no puede estar vacío.");
            return;
        }

        if (profileNameExists(name)) {
            statusLabel.setText("Ya existe un perfil con ese nombre.");
            return;
        }

        Profile profile = buildProfileFromCurrentState(name);
        profiles.add(profile);

        try {
            ProfileManager.saveProfiles(profiles);
            profileBox.getItems().setAll(profiles);
            profileBox.getSelectionModel().select(profile);
            prefs.put("lastProfileName", profile.name);
            statusLabel.setText("Perfil creado: " + profile.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Error guardando perfil: " + ex.getMessage());
        }
    }

    private void saveSelectedProfile() {
        Profile selected = profileBox.getValue();

        if (selected == null) {
            createNewProfile();
            return;
        }

        Profile updated = buildProfileFromCurrentState(selected.name);

        selected.username = updated.username;
        selected.ram = updated.ram;
        selected.version = updated.version;
        selected.type = updated.type;
        selected.skinPath = updated.skinPath;
        selected.capePath = updated.capePath;

        try {
            ProfileManager.saveProfiles(profiles);
            Profile current = profileBox.getValue();
            profileBox.getItems().setAll(profiles);
            profileBox.getSelectionModel().select(current);
            prefs.put("lastProfileName", selected.name);
            statusLabel.setText("Perfil guardado: " + selected.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Error guardando perfil: " + ex.getMessage());
        }
    }

    private void deleteSelectedProfile() {
        final Profile selected = profileBox.getValue();

        if (selected == null) {
            statusLabel.setText("No hay perfil seleccionado.");
            return;
        }

        if (profiles.size() <= 1) {
            statusLabel.setText("No puedes eliminar el único perfil.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar perfil");
        confirm.setHeaderText("¿Eliminar este perfil?");
        confirm.setContentText(selected.name);

        java.util.Optional<ButtonType> result = confirm.showAndWait();

        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        profiles.remove(selected);

        try {
            ProfileManager.saveProfiles(profiles);

            profileBox.getItems().setAll(profiles);

            if (!profiles.isEmpty()) {
                profileBox.getSelectionModel().selectFirst();
                prefs.put("lastProfileName", profileBox.getValue().name);
            }

            statusLabel.setText("Perfil eliminado: " + selected.name);
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Error eliminando perfil: " + ex.getMessage());
        }
    }

    private void applyProfile(Profile profile) {
        if (profile == null) {
            return;
        }

        applyingProfile = true;

        try {
            if (profile.username != null) {
                usernameField.setText(profile.username);
            }

            if (profile.ram > 0) {
                ramSlider.setValue(profile.ram);
            }

            if (profile.skinPath != null && !profile.skinPath.trim().isEmpty()) {
                File skin = new File(profile.skinPath);

                if (skin.exists()) {
                    selectedSkinFile = skin;
                } else {
                    selectedSkinFile = null;
                }
            } else {
                selectedSkinFile = null;
            }

            if (profile.capePath != null && !profile.capePath.trim().isEmpty()) {
                File cape = new File(profile.capePath);

                if (cape.exists()) {
                    selectedCapeFile = cape;
                } else {
                    selectedCapeFile = null;
                }
            } else {
                selectedCapeFile = null;
            }

            updateAvatar(usernameField.getText());

            if (profile.version != null && !profile.version.trim().isEmpty()) {
                selectVersionById(profile.version);
                prefs.put("lastVersion", profile.version);
            }

            prefs.put("lastProfileName", profile.name == null ? "" : profile.name);

            statusLabel.setText("Perfil aplicado: " + profile.name);
        } finally {
            applyingProfile = false;
        }
    }



    private Profile buildProfileFromCurrentState(String name) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        int ram = (int) ramSlider.getValue();

        String version = "";

        if (versionBox != null && versionBox.getValue() != null) {
            version = versionBox.getValue().id;
        }

        String skinPath = "";

        if (selectedSkinFile != null && selectedSkinFile.exists()) {
            skinPath = selectedSkinFile.getAbsolutePath();
        }

        String capePath = "";

        if (selectedCapeFile != null && selectedCapeFile.exists()) {
            capePath = selectedCapeFile.getAbsolutePath();
        }

        return new Profile(
                name,
                username,
                ram,
                version,
                detectProfileType(version),
                skinPath,
                capePath
        );
    }

    private boolean profileNameExists(String name) {
        if (name == null) {
            return false;
        }

        for (Profile p : profiles) {
            if (p.name != null && p.name.equalsIgnoreCase(name.trim())) {
                return true;
            }
        }

        return false;
    }

    private String detectProfileType(String versionId) {
        if (versionId == null) {
            return "vanilla";
        }

        String lower = versionId.toLowerCase();

        if (lower.contains("fabric")) {
            return "fabric";
        }

        if (lower.contains("forge")) {
            return "forge";
        }

        if (lower.contains("quilt")) {
            return "quilt";
        }

        if (lower.contains("optifine")) {
            return "optifine";
        }

        return "vanilla";
    }

    private void selectVersionById(String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) {
            return;
        }

        if (versionBox == null || versionBox.getItems() == null || versionBox.getItems().isEmpty()) {
            return;
        }

        for (VersionEntry v : versionBox.getItems()) {
            if (v != null && v.id != null && v.id.equals(versionId)) {
                versionBox.getSelectionModel().select(v);
                return;
            }
        }
    }

    private void loadSettings() {
        String lastUser = prefs.get("username", "Steve");
        int lastRam = prefs.getInt("ram", 2);

        String skinPath = prefs.get("customSkinFile", "");
        String capePath = prefs.get("customCapeFile", "");

        if (!skinPath.isEmpty()) {
            File f = new File(skinPath);

            if (f.exists()) {
                selectedSkinFile = f;
            }
        }

        if (!capePath.isEmpty()) {
            File f = new File(capePath);

            if (f.exists()) {
                selectedCapeFile = f;
            }
        }

        usernameField.setText(lastUser);
        updateAvatar(lastUser);
        ramSlider.setValue(lastRam);
    }

    private void saveSettings() {
        prefs.put("username", usernameField.getText().trim());
        prefs.putInt("ram", (int) ramSlider.getValue());

        if (versionBox.getValue() != null) {
            prefs.put("lastVersion", versionBox.getValue().id);
        }

        if (selectedSkinFile != null && selectedSkinFile.exists()) {
            prefs.put("customSkinFile", selectedSkinFile.getAbsolutePath());
        }

        if (selectedCapeFile != null && selectedCapeFile.exists()) {
            prefs.put("customCapeFile", selectedCapeFile.getAbsolutePath());
        }
        if (profileBox != null && profileBox.getValue() != null) {
            prefs.put("lastProfileName", profileBox.getValue().name);
        }
    }

    private void showCrashDialog(final CrashAnalyzer.CrashException crash) {
        CrashAnalyzer.CrashInfo info = crash.getCrashInfo();

        Stage dialog = new Stage();
        dialog.setTitle("Minecraft se cerró con error");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label(info.title);
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #dc2626;");

        Label subtitle = new Label("Código de salida: " + crash.getExitCode());
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));

        VBox causeCard = new VBox(8);
        causeCard.getStyleClass().add("namemc-card");

        Label causeTitle = new Label("Causa probable");
        causeTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label causeText = new Label(info.cause);
        causeText.setWrapText(true);
        causeText.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

        causeCard.getChildren().addAll(causeTitle, causeText);

        VBox solutionCard = new VBox(8);
        solutionCard.getStyleClass().add("namemc-card");

        Label solutionTitle = new Label("Solución recomendada");
        solutionTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label solutionText = new Label(info.solution);
        solutionText.setWrapText(true);
        solutionText.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");

        solutionCard.getChildren().addAll(solutionTitle, solutionText);

        VBox detailsCard = new VBox(8);
        detailsCard.getStyleClass().add("namemc-card");

        Label detailsTitle = new Label("Detalles técnicos");
        detailsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        TextArea detailsArea = new TextArea(info.details);
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefHeight(180);
        detailsArea.getStyleClass().add("console-output");

        detailsCard.getChildren().addAll(detailsTitle, detailsArea);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = new Button("Copiar diagnóstico");
        copyBtn.getStyleClass().add("secondary-button");

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("button");

        copyBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                String text =
                        "Título: " + crash.getCrashInfo().title + "\n\n" +
                                "Causa: " + crash.getCrashInfo().cause + "\n\n" +
                                "Solución: " + crash.getCrashInfo().solution + "\n\n" +
                                "Detalles:\n" + crash.getCrashInfo().details + "\n\n" +
                                "Log completo:\n" + crash.getGameLog();

                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(text);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });

        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Stage s = (Stage) closeBtn.getScene().getWindow();
                s.close();
            }
        });

        buttons.getChildren().addAll(copyBtn, closeBtn);

        content.getChildren().addAll(causeCard, solutionCard, detailsCard, buttons);

        root.setTop(header);
        root.setCenter(content);

        Scene scene = new Scene(root, 680, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(620);
        dialog.setMinHeight(560);
        dialog.show();
    }

    private void loadVersions() {
        System.out.println("Starting loadVersions...");
        statusLabel.setText("Cargando lista de versiones...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        final Task<List<VersionEntry>> task = new Task<List<VersionEntry>>() {
            @Override
            protected List<VersionEntry> call() throws Exception {
                try {
                    System.out.println("Task starting, calling VersionManager.getVersions()");
                    List<VersionEntry> versions = VersionManager.getVersions();
                    System.out.println("Got " + versions.size() + " versions, returning...");
                    return versions;
                } catch (Exception e) {
                    System.out.println("Error in Task.call(): " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                System.out.println("Task SUCCEEDED, updating UI...");
                try {
                    allVersions = task.getValue();
                    System.out.println("Retrieved value, items: " + allVersions.size());
                    
                    // Ensure we update UI on JavaFX thread
                    Platform.runLater(() -> {
                        try {
                            versionBox.getItems().setAll(allVersions);
                            System.out.println("Items set in ComboBox");

                            Instance selectedInst = getCurrentInstance();

                            if (selectedInstance != null && selectedInstance.version != null && !selectedInstance.version.trim().isEmpty()) {
                                selectVersionById(selectedInstance.version);
                            } else {
                                Profile selectedProfile = profileBox == null ? null : profileBox.getValue();

                                if (selectedProfile != null && selectedProfile.version != null && !selectedProfile.version.trim().isEmpty()) {
                                    selectVersionById(selectedProfile.version);
                                } else {
                                    String lastVer = prefs.get("lastVersion", "");

                                    if (!lastVer.isEmpty()) {
                                        for (VersionEntry v : allVersions) {
                                            if (v.id.equals(lastVer)) {
                                                versionBox.getSelectionModel().select(v);
                                                break;
                                            }
                                        }
                                    } else if (!allVersions.isEmpty()) {
                                        versionBox.getSelectionModel().selectFirst();
                                    }
                                }
                            }

                            statusLabel.setText("Listo para jugar.");
                            progressBar.setVisible(false);
                            System.out.println("UI updated with " + allVersions.size() + " versions");
                        } catch (Exception e) {
                            System.out.println("Error in Platform.runLater: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    System.out.println("Error in onSucceeded: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                if (ex instanceof CrashAnalyzer.CrashException) {
                    CrashAnalyzer.CrashException crash = (CrashAnalyzer.CrashException) ex;
                    showCrashDialog(crash);
                    statusLabel.setText("❌ Minecraft crasheó: " + crash.getCrashInfo().title);
                } else {
                    statusLabel.setText("❌ Error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        task.setOnCancelled(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                System.out.println("Task was CANCELLED");
                statusLabel.setText("Carga cancelada");
                progressBar.setVisible(false);
            }
        });

        System.out.println("Starting thread for task...");
        new Thread(task).start();
        System.out.println("Thread started");
    }

    private void repairSelectedVersion(final Button repairBtn) {
        final VersionEntry entry = versionBox.getValue();

        if (entry == null) {
            statusLabel.setText("⚠️ Selecciona una versión para reparar.");
            return;
        }

        repairBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Reparando instalación de " + entry.id + "...");

        final Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                VersionManager.repairVersion(entry.id);
                return null;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                repairBtn.setDisable(false);
                progressBar.setVisible(false);
                statusLabel.setText("✅ Instalación reparada: " + entry.id);
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                repairBtn.setDisable(false);
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                if (ex != null) {
                    statusLabel.setText("❌ Error reparando: " + ex.getMessage());
                    ex.printStackTrace();
                } else {
                    statusLabel.setText("❌ Error reparando instalación.");
                }
            }
        });

        Thread t = new Thread(task, "Repair-Version");
        t.setDaemon(true);
        t.start();
    }

    private void launchGame() {
        final VersionEntry entry = versionBox.getValue();
        final String username = usernameField.getText().trim();
        final int ram = (int) ramSlider.getValue();

        if (entry == null || username.isEmpty()) {
            statusLabel.setText("⚠️ Por favor, pon tu nombre y elige una versión.");
            return;
        }

        if (!runPreLaunchCheck(entry.id)) {
            return;
        }

        saveSettings();

        try {
            saveSelectedInstance();
        } catch (Exception ex) {
            System.err.println("[Instance] No se pudo guardar instancia antes de jugar: " + ex.getMessage());
        }

        final File gameDir = getCurrentInstanceGameDir();

        final Instance launchInstance = getCurrentInstance();

        if (launchInstance != null) {
            launchInstance.lastPlayedAt = System.currentTimeMillis();
            currentSessionStartMillis = System.currentTimeMillis();

            try {
                InstanceManager.saveInstance(launchInstance);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            updateDashboardStats(launchInstance);
        }

        writeInstanceLaunchLog(entry.id, username, ram, gameDir);

        System.out.println("[LAUNCHER-DEBUG] Preparando lanzamiento:");
        System.out.println("[LAUNCHER-DEBUG] Versión: " + entry.id);
        System.out.println("[LAUNCHER-DEBUG] Usuario: " + username);
        System.out.println("[LAUNCHER-DEBUG] RAM: " + ram + " GB");
        System.out.println("[LAUNCHER-DEBUG] GameDir instancia: " + gameDir.getAbsolutePath());

        statusLabel.setText("Preparando juego...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        final Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                MinecraftLauncher.launch(
                        entry.id,
                        username,
                        ram,
                        gameDir,
                        getCurrentCustomClientJar(),
                        getCurrentJvmArgs()
                );
                return null;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                statusLabel.setText("✅ Juego en ejecución.");
                showToast("Juego en ejecución", "success");
                progressBar.setVisible(false);

                if (dashboardGameStatusLabel != null) {
                    dashboardGameStatusLabel.setText("En ejecución");
                }

                Instance instance = getCurrentInstance();

                if (instance != null) {
                    if (instance.totalPlayTimeSeconds < 0) {
                        instance.totalPlayTimeSeconds = 0;
                    }

                    // Sesión mínima registrada. Luego se puede mejorar con watcher real.
                    instance.totalPlayTimeSeconds += 60;

                    try {
                        InstanceManager.saveInstance(instance);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    updateDashboardStats(instance);
                }
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {

                if (dashboardGameStatusLabel != null) {
                    dashboardGameStatusLabel.setText("Error");
                }
                progressBar.setVisible(false);
                Throwable ex = task.getException();

                if (ex instanceof CrashAnalyzer.CrashException) {
                    CrashAnalyzer.CrashException crash = (CrashAnalyzer.CrashException) ex;
                    showCrashDialog(crash);
                    statusLabel.setText("❌ Minecraft crasheó: " + crash.getCrashInfo().title);
                    showToast("Minecraft Crasheo", "error");
                } else {
                    String msg = ex == null || ex.getMessage() == null ? "Error desconocido" : ex.getMessage();
                    statusLabel.setText("❌ Error iniciando Minecraft: " + msg);
                    showToast("Error iniciando Minecraft", "error");

                    if (ex != null) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        Thread t = new Thread(task, "Minecraft-Launch-Task");
        t.setDaemon(true);
        t.start();
    }

    private void redirectLogs(final TextArea textArea) {
        OutputStream out = new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) throws IOException {
                buffer.write(b);

                if (b == '\n') {
                    flushBuffer();
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) throws IOException {
                buffer.write(b, off, len);

                for (int i = off; i < off + len; i++) {
                    if (b[i] == '\n') {
                        flushBuffer();
                        break;
                    }
                }
            }

            @Override
            public synchronized void flush() throws IOException {
                flushBuffer();
            }

            private void flushBuffer() {
                if (buffer.size() == 0) {
                    return;
                }

                String raw = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                buffer.reset();

                final String cleaned = cleanConsoleText(raw);

                if (cleaned.isEmpty()) {
                    return;
                }

                if (logWriter != null) {
                    logWriter.print(cleaned);
                    logWriter.flush();
                }

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        textArea.appendText(cleaned);

                        // Evitar que el TextArea crezca infinitamente.
                        int maxChars = 120000;
                        if (textArea.getLength() > maxChars) {
                            textArea.deleteText(0, textArea.getLength() - maxChars);
                        }

                        // Mantener siempre abajo.
                        textArea.positionCaret(textArea.getLength());
                        textArea.setScrollTop(Double.MAX_VALUE);
                    }
                });
            }
        };

        try {
            PrintStream ps = new PrintStream(out, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
        } catch (Exception ex) {
            PrintStream ps = new PrintStream(out, true);
            System.setOut(ps);
            System.setErr(ps);
        }
    }

    private void downloadCurseForgeFile(String urlStr, File targetFile) throws Exception {
        targetFile.getParentFile().mkdirs();

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;

        try {
            java.net.URL url = java.net.URI.create(urlStr).toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 MinecraftLauncher/1.0");
            conn.setRequestProperty("Accept", "*/*");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                String body = "";

                try {
                    java.io.InputStream err = conn.getErrorStream();

                    if (err != null) {
                        java.util.Scanner scanner = new java.util.Scanner(err, "UTF-8").useDelimiter("\\A");
                        body = scanner.hasNext() ? scanner.next() : "";
                        scanner.close();
                        err.close();
                    }
                } catch (Exception ignored) {
                }

                throw new Exception("HTTP " + code + " al descargar desde CurseForge. " + body);
            }

            in = conn.getInputStream();
            out = new java.io.FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }
        }

        if (!targetFile.exists() || targetFile.length() == 0) {
            throw new Exception("La descarga quedó vacía.");
        }
    }



    private void exportCurrentInstanceToZip() {
        Instance instance = getCurrentInstance();

        if (instance == null) {
            statusLabel.setText("No hay instancia seleccionada para exportar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar instancia");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo ZIP", "*.zip"));
        chooser.setInitialFileName(InstanceManager.safeName(instance.name) + ".zip");

        File selected = chooser.showSaveDialog(null);

        if (selected == null) {
            return;
        }

        if (!selected.getName().toLowerCase().endsWith(".zip")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".zip");
        }

        final File targetZip = selected;
        final Instance selectedInstance = instance;

        statusLabel.setText("Exportando instancia " + selectedInstance.name + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                InstanceManager.saveInstance(selectedInstance);
                InstanceManager.exportInstance(selectedInstance, targetZip);
                return null;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);
                statusLabel.setText("✅ Instancia exportada: " + targetZip.getName());
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                if (ex != null) {
                    statusLabel.setText("❌ Error exportando instancia: " + ex.getMessage());
                    ex.printStackTrace();
                } else {
                    statusLabel.setText("❌ Error exportando instancia.");
                }
            }
        });

        Thread t = new Thread(task, "Export-Instance");
        t.setDaemon(true);
        t.start();
    }

    private void importInstanceFromZip() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar instancia");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo ZIP", "*.zip"));

        File selected = chooser.showOpenDialog(null);

        if (selected == null) {
            return;
        }

        final File zipFile = selected;

        statusLabel.setText("Importando instancia...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Instance> task = new Task<Instance>() {
            @Override
            protected Instance call() throws Exception {
                return InstanceManager.importInstance(zipFile);
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Instance imported = task.getValue();

                instances.add(imported);
                refreshInstanceViews();
                selectInstance(imported);

                prefs.put("lastInstanceName", imported.name);

                statusLabel.setText("✅ Instancia importada: " + imported.name);
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                if (ex != null) {
                    statusLabel.setText("❌ Error importando instancia: " + ex.getMessage());
                    ex.printStackTrace();
                } else {
                    statusLabel.setText("❌ Error importando instancia.");
                }
            }
        });

        Thread t = new Thread(task, "Import-Instance");
        t.setDaemon(true);
        t.start();
    }

    private boolean showPreLaunchIssuesDialog(final List<PreLaunchChecker.PreLaunchIssue> issues, boolean hasError) {
        final Dialog<Boolean> dialog = new Dialog<Boolean>();
        dialog.setTitle("Comprobación antes de jugar");

        if (hasError) {
            dialog.setHeaderText("Se detectaron problemas importantes.");
        } else {
            dialog.setHeaderText("Se detectaron posibles advertencias.");
        }

        ButtonType repairButton = new ButtonType("Reparar automáticamente", ButtonBar.ButtonData.APPLY);
        ButtonType launchAnywayButton = new ButtonType("Jugar igualmente", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(repairButton, launchAnywayButton, cancelButton);

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setPrefWidth(680);
        root.setMaxWidth(680);

        Label intro = new Label(
                hasError
                        ? "El launcher encontró problemas que pueden impedir que Minecraft inicie correctamente."
                        : "El launcher encontró advertencias que podrían causar problemas."
        );
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px;");

        VBox issuesBox = new VBox(10);
        issuesBox.setPadding(new Insets(4));

        if (issues != null) {
            for (PreLaunchChecker.PreLaunchIssue issue : issues) {
                if (issue == null) {
                    continue;
                }

                VBox card = new VBox(6);
                card.getStyleClass().add("namemc-card");
                card.setMaxWidth(Double.MAX_VALUE);

                Label title = new Label(issue.severity + " · " + issue.title);

                if ("ERROR".equalsIgnoreCase(issue.severity)) {
                    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 900; -fx-text-fill: #dc2626;");
                } else if ("WARNING".equalsIgnoreCase(issue.severity)) {
                    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 900; -fx-text-fill: #d97706;");
                } else {
                    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 900; -fx-text-fill: #2563eb;");
                }

                Label desc = new Label(issue.description);
                desc.setWrapText(true);
                desc.setMaxWidth(610);
                desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

                Label solution = new Label("Solución: " + issue.solution);
                solution.setWrapText(true);
                solution.setMaxWidth(610);
                solution.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

                card.getChildren().addAll(title, desc, solution);
                issuesBox.getChildren().add(card);
            }
        }

        ScrollPane issuesScroll = new ScrollPane(issuesBox);
        issuesScroll.setFitToWidth(true);
        issuesScroll.setPrefHeight(340);
        issuesScroll.setMaxHeight(340);
        issuesScroll.setMinHeight(220);
        issuesScroll.getStyleClass().add("prelaunch-scroll");

        Label countLabel = new Label("Problemas detectados: " + (issues == null ? 0 : issues.size()));
        countLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #6b7280;");

        Label repairHint = new Label(
                "La reparación automática puede instalar mods requeridos como Fabric API, Sodium o Iris. " +
                        "Para duplicados o mods de versión incorrecta, abrirá el gestor de Mods para revisión manual."
        );
        repairHint.setWrapText(true);
        repairHint.setMaxWidth(640);
        repairHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        root.getChildren().addAll(intro, countLabel, issuesScroll, repairHint);

        dialog.getDialogPane().setContent(root);

        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setResultConverter(new javafx.util.Callback<ButtonType, Boolean>() {
            @Override
            public Boolean call(ButtonType buttonType) {
                if (buttonType == repairButton) {
                    attemptAutoFixPreLaunchIssues(issues);
                    return false;
                }

                if (buttonType == launchAnywayButton) {
                    return true;
                }

                return false;
            }
        });

        java.util.Optional<Boolean> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return false;
        }

        return result.get();
    }

    private void attemptAutoFixPreLaunchIssues(final List<PreLaunchChecker.PreLaunchIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            statusLabel.setText("No hay problemas que reparar.");
            return;
        }

        statusLabel.setText("Reparando problemas detectados...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                boolean openedModsPanelNeeded = false;

                for (PreLaunchChecker.PreLaunchIssue issue : issues) {
                    if (issue == null || issue.title == null) {
                        continue;
                    }

                    String title = issue.title.toLowerCase();

                    if (title.contains("fabric api")) {
                        installRequiredModrinthModForCurrentInstance("fabric-api", "Fabric API");
                    } else if (title.contains("iris installed without sodium")) {
                        installRequiredModrinthModForCurrentInstance("sodium", "Sodium");
                    } else if (title.contains("shaders installed but iris is missing")) {
                        installRequiredModrinthModForCurrentInstance("iris", "Iris Shaders");
                    } else if (title.contains("duplicate mods")) {
                        openedModsPanelNeeded = true;
                    } else if (title.contains("version mismatch")) {
                        openedModsPanelNeeded = true;
                    }
                }

                final boolean openMods = openedModsPanelNeeded;

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (openMods) {
                            showInstalledModsDialog();
                        }
                    }
                });

                return null;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);
                progressBar.setProgress(0);
                statusLabel.setText("✅ Reparación automática terminada. Pulsa Jugar otra vez para comprobar.");
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);
                progressBar.setProgress(0);

                Throwable ex = task.getException();

                if (ex != null) {
                    statusLabel.setText("❌ Error en reparación automática: " + ex.getMessage());
                    ex.printStackTrace();
                } else {
                    statusLabel.setText("❌ Error en reparación automática.");
                }
            }
        });

        Thread t = new Thread(task, "PreLaunch-AutoFix");
        t.setDaemon(true);
        t.start();
    }

    private void showWorldManagerDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Gestor de mundos");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Mundos");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label subtitle = new Label("Gestiona los mundos de la instancia actual.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));
        content.setStyle("-fx-background-color: #f6f8fb;");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("secondary-button");

        Button openSavesBtn = new Button("Abrir saves");
        openSavesBtn.getStyleClass().add("secondary-button");

        Button backupBtn = new Button("Crear backup");
        backupBtn.getStyleClass().add("button");
        backupBtn.setDisable(true);

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actions.getChildren().addAll(refreshBtn, openSavesBtn, spacer, backupBtn, deleteBtn);

        final ListView<File> worldsList = new ListView<File>();
        worldsList.getStyleClass().add("list-view");
        VBox.setVgrow(worldsList, Priority.ALWAYS);

        worldsList.setCellFactory(new javafx.util.Callback<ListView<File>, ListCell<File>>() {
            @Override
            public ListCell<File> call(ListView<File> listView) {
                return new ListCell<File>() {
                    @Override
                    protected void updateItem(File item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        VBox box = new VBox(5);
                        box.setPadding(new Insets(10));

                        Label name = new Label(getWorldDisplayName(item));
                        name.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #111827;");

                        Label meta = new Label(
                                item.getName() + " · " + formatFileSize(getDirectorySize(item))
                        );
                        meta.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

                        box.getChildren().addAll(name, meta);

                        setText(null);
                        setGraphic(box);
                    }
                };
            }
        });

        final Label status = new Label("Cargando mundos...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        content.getChildren().addAll(actions, worldsList, status);

        worldsList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<File>() {
            @Override
            public void changed(ObservableValue<? extends File> observable, File oldValue, File newValue) {
                boolean selected = newValue != null;
                backupBtn.setDisable(!selected);
                deleteBtn.setDisable(!selected);
            }
        });

        refreshBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                refreshWorldsList(worldsList, status);
            }
        });

        openSavesBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(getSavesDir());
            }
        });

        backupBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File selected = worldsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                try {
                    File backup = backupWorld(selected);
                    status.setText("Backup creado: " + backup.getName());
                    showToast("Backup creado: " + backup.getName(), "success");
                } catch (Exception ex) {
                    status.setText("Error creando backup: " + ex.getMessage());
                    showToast("Error creando backup", "error");
                }
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File selected = worldsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Eliminar mundo");
                confirm.setHeaderText("¿Eliminar este mundo?");
                confirm.setContentText(getWorldDisplayName(selected));

                java.util.Optional<ButtonType> result = confirm.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    deleteDirectory(selected);
                    refreshWorldsList(worldsList, status);
                    showToast("Mundo eliminado", "info");
                }
            }
        });

        root.setTop(header);
        root.setCenter(content);

        refreshWorldsList(worldsList, status);

        Scene scene = new Scene(root, 760, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(540);
        dialog.show();
    }

    private File getSavesDir() {
        File dir = new File(getCurrentInstanceGameDir(), "saves");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private void refreshWorldsList(ListView<File> list, Label status) {
        File savesDir = getSavesDir();

        list.getItems().clear();

        File[] worlds = savesDir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && new File(file, "level.dat").exists();
            }
        });

        if (worlds == null || worlds.length == 0) {
            status.setText("No hay mundos en esta instancia.");
            return;
        }

        java.util.Arrays.sort(worlds, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        for (File world : worlds) {
            list.getItems().add(world);
        }

        status.setText("Mundos encontrados: " + worlds.length);
    }

    private String getWorldDisplayName(File worldDir) {
        if (worldDir == null) {
            return "Mundo";
        }

        return worldDir.getName();
    }

    private File backupWorld(File worldDir) throws Exception {
        if (worldDir == null || !worldDir.exists()) {
            throw new Exception("Mundo no válido.");
        }

        File backupsDir = new File(getCurrentInstanceGameDir(), "backups");

        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
        }

        String safeName = worldDir.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String timestamp = java.time.LocalDateTime.now()
                .toString()
                .replace(":", "-")
                .replace(".", "-");

        File target = new File(backupsDir, safeName + "-" + timestamp + ".zip");

        zipDirectory(worldDir, target);

        return target;
    }

    private void zipDirectory(File sourceDir, File targetZip) throws Exception {
        java.util.zip.ZipOutputStream zos = null;

        try {
            zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(targetZip));
            zipDirectoryRecursive(sourceDir, sourceDir, zos);
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
    }

    private void zipDirectoryRecursive(File rootDir, File current, java.util.zip.ZipOutputStream zos) throws Exception {
        File[] files = current.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            String relative = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");

            if (file.isDirectory()) {
                zipDirectoryRecursive(rootDir, file, zos);
            } else {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(relative);
                zos.putNextEntry(entry);

                java.io.FileInputStream in = null;

                try {
                    in = new java.io.FileInputStream(file);

                    byte[] buffer = new byte[8192];
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }

                zos.closeEntry();
            }
        }
    }

    private long getDirectorySize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }

        if (file.isFile()) {
            return file.length();
        }

        long size = 0;

        File[] children = file.listFiles();

        if (children != null) {
            for (File child : children) {
                size += getDirectorySize(child);
            }
        }

        return size;
    }

    private void installRequiredModrinthModForCurrentInstance(String slug, String displayName) throws Exception {
        if (slug == null || slug.trim().isEmpty()) {
            return;
        }

        if (hasActiveModInCurrentInstance(slug)) {
            System.out.println("[AutoFix] " + displayName + " ya está instalado.");
            return;
        }

        final String installingText = "Instalando " + displayName + "...";

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(installingText);
            }
        });

        String mcVersion = getCurrentMinecraftVersionForMods();

        if (mcVersion == null || mcVersion.trim().isEmpty()) {
            throw new Exception("No se pudo detectar la versión de Minecraft actual.");
        }

        ModrinthClient.ModVersionFile fileData = ModrinthClient.getLatestVersionFile(
                slug,
                mcVersion,
                "mod"
        );

        File modsDir = getModsDirectory();
        modsDir.mkdirs();

        File target = new File(modsDir, safeModFileName(fileData.filename));

        if (target.exists() && target.length() > 0) {
            System.out.println("[AutoFix] Ya existe archivo: " + target.getName());
            return;
        }

        downloadModFile(fileData.url, target);

        ModrinthClient.ModResult marker = new ModrinthClient.ModResult(
                slug,
                displayName,
                "Instalado automáticamente por reparación previa al lanzamiento.",
                slug,
                "mod"
        );

        markContentInstalled(slug, "mod", displayName, target);
        markLogicalContentInstalled(marker, target);

        System.out.println("[AutoFix] Instalado: " + target.getName());
    }

    private VBox createDashboardStatCard(String title, String value) {
        VBox card = new VBox(4);
        card.getStyleClass().add("dashboard-stat-card");
        card.setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-stat-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("dashboard-stat-value");
        valueLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, valueLabel);

        return card;
    }

    private boolean hasActiveModInCurrentInstance(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }

        File modsDir = getModsDirectory();

        if (!modsDir.exists()) {
            return false;
        }

        File[] files = modsDir.listFiles();

        if (files == null) {
            return false;
        }

        String key = keyword.toLowerCase();

        for (File file : files) {
            String name = file.getName().toLowerCase();

            if (!name.endsWith(".jar")) {
                continue;
            }

            if (name.contains(key)) {
                return true;
            }
        }

        return false;
    }

    private boolean runPreLaunchCheck(String versionId) {
        List<PreLaunchChecker.PreLaunchIssue> issues = PreLaunchChecker.check(getCurrentInstance(), versionId);

        if (issues == null || issues.isEmpty()) {
            return true;
        }

        boolean hasError = false;

        for (PreLaunchChecker.PreLaunchIssue issue : issues) {
            if ("ERROR".equalsIgnoreCase(issue.severity)) {
                hasError = true;
                break;
            }
        }

        if (hasError) {
            showPreLaunchIssuesDialog(issues, true);
            return false;
        }

        return showPreLaunchIssuesDialog(issues, false);
    }

    private void showFabricLoaderSelectionDialog(final VersionEntry version, final Button fabricBtn) {
        final Stage dialog = new Stage();
        dialog.setTitle("Instalar Fabric");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Instalar Fabric");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label subtitle = new Label("Elige la versión de Fabric Loader para " + version.id + ".");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(12);
        content.setPadding(new Insets(0, 24, 20, 24));

        final ComboBox<FabricManager.FabricLoaderVersion> loaderBox = new ComboBox<FabricManager.FabricLoaderVersion>();
        loaderBox.setMaxWidth(Double.MAX_VALUE);
        loaderBox.setPromptText("Cargando loaders...");

        final Label status = new Label("Cargando versiones de Fabric Loader...");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        ProgressBar bar = new ProgressBar(-1);
        bar.setMaxWidth(Double.MAX_VALUE);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button installBtn = new Button("Instalar");
        installBtn.getStyleClass().add("button");
        installBtn.setDisable(true);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("secondary-button");

        buttons.getChildren().addAll(cancelBtn, installBtn);

        content.getChildren().addAll(loaderBox, status, bar, buttons);

        root.setTop(header);
        root.setCenter(content);

        Scene scene = new Scene(root, 520, 300);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.show();

        Thread loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<FabricManager.FabricLoaderVersion> loaders = FabricManager.getLoaderVersions(version.id);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            loaderBox.getItems().setAll(loaders);

                            if (!loaders.isEmpty()) {
                                loaderBox.getSelectionModel().selectFirst();
                                installBtn.setDisable(false);
                                status.setText("Selecciona un loader. Recomendado: el primero estable.");
                            } else {
                                status.setText("No se encontraron loaders para esta versión.");
                            }

                            bar.setVisible(false);
                        }
                    });
                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Error cargando loaders: " + ex.getMessage());
                            bar.setVisible(false);
                        }
                    });
                }
            }
        }, "Load-Fabric-Loaders");

        loadThread.setDaemon(true);
        loadThread.start();

        cancelBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        installBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FabricManager.FabricLoaderVersion selected = loaderBox.getValue();

                if (selected == null) {
                    status.setText("Selecciona un loader.");
                    return;
                }

                dialog.close();

                installFabricWithLoader(version, selected.version, fabricBtn);
            }
        });
    }

    private void showScreenshotsDialog() {
        final Stage dialog = new Stage();
        dialog.setTitle("Capturas");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Capturas");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #111827;");

        Label subtitle = new Label("Capturas de pantalla de la instancia actual.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));
        content.setStyle("-fx-background-color: #f6f8fb;");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("secondary-button");

        Button openFolderBtn = new Button("Abrir carpeta");
        openFolderBtn.getStyleClass().add("secondary-button");

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actions.getChildren().addAll(refreshBtn, openFolderBtn, spacer, deleteBtn);

        final ListView<File> screenshotsList = new ListView<File>();
        screenshotsList.getStyleClass().add("list-view");
        VBox.setVgrow(screenshotsList, Priority.ALWAYS);

        screenshotsList.setCellFactory(new javafx.util.Callback<ListView<File>, ListCell<File>>() {
            @Override
            public ListCell<File> call(ListView<File> listView) {
                return new ListCell<File>() {
                    @Override
                    protected void updateItem(final File item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(10));

                        ImageView preview = new ImageView(new Image(item.toURI().toString(), 90, 54, true, true));
                        preview.setFitWidth(90);
                        preview.setFitHeight(54);
                        preview.setPreserveRatio(true);
                        preview.setSmooth(true);

                        VBox info = new VBox(5);

                        Label name = new Label(item.getName());
                        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 900; -fx-text-fill: #111827;");

                        Label meta = new Label(formatFileSize(item.length()));
                        meta.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

                        info.getChildren().addAll(name, meta);
                        row.getChildren().addAll(preview, info);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        final Label status = new Label("Cargando capturas...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        content.getChildren().addAll(actions, screenshotsList, status);

        screenshotsList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<File>() {
            @Override
            public void changed(ObservableValue<? extends File> observable, File oldValue, File newValue) {
                deleteBtn.setDisable(newValue == null);
            }
        });

        screenshotsList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    File selected = screenshotsList.getSelectionModel().getSelectedItem();

                    if (selected != null) {
                        openFile(selected);
                    }
                }
            }
        });

        refreshBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                refreshScreenshotsList(screenshotsList, status);
            }
        });

        openFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openFolder(getScreenshotsDir());
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File selected = screenshotsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                if (selected.delete()) {
                    showToast("Captura eliminada", "info");
                }

                refreshScreenshotsList(screenshotsList, status);
            }
        });

        root.setTop(header);
        root.setCenter(content);

        refreshScreenshotsList(screenshotsList, status);

        Scene scene = new Scene(root, 760, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(540);
        dialog.show();
    }

    private File getScreenshotsDir() {
        File dir = new File(getCurrentInstanceGameDir(), "screenshots");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private void refreshScreenshotsList(ListView<File> list, Label status) {
        File dir = getScreenshotsDir();

        list.getItems().clear();

        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            }
        });

        if (files == null || files.length == 0) {
            status.setText("No hay capturas en esta instancia.");
            return;
        }

        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        for (File file : files) {
            list.getItems().add(file);
        }

        status.setText("Capturas encontradas: " + files.length);
    }

    private void openFile(File file) {
        try {
            if (file != null && file.exists()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ex) {
            statusLabel.setText("No se pudo abrir archivo: " + ex.getMessage());
        }
    }

    private void updateDashboardStats(Instance instance) {
        if (instance == null) {
            return;
        }

        if (dashboardLastPlayedLabel != null) {
            dashboardLastPlayedLabel.setText(formatLastPlayed(instance.lastPlayedAt));
        }

        if (dashboardPlayTimeLabel != null) {
            dashboardPlayTimeLabel.setText(formatPlayTime(instance.totalPlayTimeSeconds));
        }

        if (dashboardContentStatsLabel != null) {
            dashboardContentStatsLabel.setText(getContentStatsForInstance(instance));
        }

        if (dashboardGameStatusLabel != null) {
            dashboardGameStatusLabel.setText("Listo");
        }
    }

    private String formatLastPlayed(long millis) {
        if (millis <= 0) {
            return "Nunca";
        }

        try {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(millis);
            java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(
                    instant,
                    java.time.ZoneId.systemDefault()
            );

            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate playedDate = date.toLocalDate();

            if (playedDate.equals(today)) {
                return "Hoy " + String.format("%02d:%02d", date.getHour(), date.getMinute());
            }

            if (playedDate.equals(today.minusDays(1))) {
                return "Ayer " + String.format("%02d:%02d", date.getHour(), date.getMinute());
            }

            return String.format(
                    "%02d/%02d/%04d",
                    playedDate.getDayOfMonth(),
                    playedDate.getMonthValue(),
                    playedDate.getYear()
            );
        } catch (Exception ex) {
            return "Desconocido";
        }
    }

    private String formatPlayTime(long seconds) {
        if (seconds <= 0) {
            return "0 min";
        }

        long minutes = seconds / 60;

        if (minutes < 60) {
            return minutes + " min";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (remainingMinutes == 0) {
            return hours + " h";
        }

        return hours + " h " + remainingMinutes + " min";
    }

    private String getContentStatsForInstance(Instance instance) {
        File gameDir = InstanceManager.getGameDir(instance);

        int mods = countFiles(new File(gameDir, "mods"), ".jar");
        int shaders = countFiles(new File(gameDir, "shaderpacks"), ".zip");
        int resourcepacks = countFiles(new File(gameDir, "resourcepacks"), ".zip");

        return mods + " mods · " + shaders + " shaders · " + resourcepacks + " texturas";
    }

    private int countFiles(File dir, final String extension) {
        if (dir == null || !dir.exists()) {
            return 0;
        }

        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(extension);
            }
        });

        return files == null ? 0 : files.length;
    }

    private void installFabricWithLoader(final VersionEntry v,
                                         final String loaderVersion,
                                         final Button fabricBtn) {
        fabricBtn.setDisable(true);
        statusLabel.setText("Instalando Fabric " + loaderVersion + " para " + v.id + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        FabricManager.install(v, loaderVersion, new FabricManager.LauncherCallback() {
            @Override
            public void onStatus(final String msg) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(msg);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedVersionId) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("✅ Fabric instalado con éxito: " + installedVersionId);
                        fabricBtn.setDisable(false);
                        progressBar.setVisible(false);
                        loadVersions();
                    }
                });
            }

            @Override
            public void onError(final String err) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("❌ Error de Fabric: " + err);
                        fabricBtn.setDisable(false);
                        progressBar.setVisible(false);
                    }
                });
            }
        });
    }

    private ModrinthClient.ModVersionFile showContentVersionSelectionDialog(final ModrinthClient.ModResult selected,
                                                                            final ContentProviderOption provider,
                                                                            final String mcVersion) throws Exception {
        final Dialog<ModrinthClient.ModVersionFile> dialog = new Dialog<ModrinthClient.ModVersionFile>();
        dialog.setTitle("Seleccionar versión");
        dialog.setHeaderText("Elige qué versión instalar de " + selected.title);

        ButtonType installButton = new ButtonType("Instalar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(installButton, cancelButton);

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setPrefWidth(620);

        final ComboBox<ModrinthClient.ModVersionFile> versionFileBox = new ComboBox<ModrinthClient.ModVersionFile>();
        versionFileBox.setMaxWidth(Double.MAX_VALUE);
        versionFileBox.setPromptText("Cargando versiones...");

        final Label status = new Label("Cargando versiones disponibles...");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        ProgressBar bar = new ProgressBar(-1);
        bar.setMaxWidth(Double.MAX_VALUE);

        root.getChildren().addAll(versionFileBox, status, bar);

        dialog.getDialogPane().setContent(root);

        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        final Button installBtn = (Button) dialog.getDialogPane().lookupButton(installButton);
        installBtn.setDisable(true);

        versionFileBox.setCellFactory(new javafx.util.Callback<ListView<ModrinthClient.ModVersionFile>, ListCell<ModrinthClient.ModVersionFile>>() {
            @Override
            public ListCell<ModrinthClient.ModVersionFile> call(ListView<ModrinthClient.ModVersionFile> listView) {
                return new ListCell<ModrinthClient.ModVersionFile>() {
                    @Override
                    protected void updateItem(ModrinthClient.ModVersionFile item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            return;
                        }

                        setText(formatVersionFileLabel(item));
                    }
                };
            }
        });

        versionFileBox.setButtonCell(new ListCell<ModrinthClient.ModVersionFile>() {
            @Override
            protected void updateItem(ModrinthClient.ModVersionFile item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    return;
                }

                setText(formatVersionFileLabel(item));
            }
        });

        Thread loader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ModrinthClient.ModVersionFile> files;

                    if (isCurseForgeProvider(provider)) {
                        files = CurseForgeClient.getVersionFiles(
                                getCurseForgeApiKey(),
                                selected.projectId,
                                mcVersion,
                                selected.projectType
                        );
                    } else {
                        files = ModrinthClient.getVersionFiles(
                                selected.projectId,
                                mcVersion,
                                selected.projectType
                        );
                    }

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            versionFileBox.getItems().setAll(files);

                            if (!files.isEmpty()) {
                                versionFileBox.getSelectionModel().selectFirst();
                                installBtn.setDisable(false);
                                status.setText("Versiones encontradas: " + files.size());
                            } else {
                                status.setText("No se encontraron versiones compatibles.");
                            }

                            bar.setVisible(false);
                        }
                    });
                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("Error cargando versiones: " + ex.getMessage());
                            bar.setVisible(false);
                        }
                    });
                }
            }
        }, "Load-Content-Versions");

        loader.setDaemon(true);
        loader.start();

        dialog.setResultConverter(new javafx.util.Callback<ButtonType, ModrinthClient.ModVersionFile>() {
            @Override
            public ModrinthClient.ModVersionFile call(ButtonType buttonType) {
                if (buttonType == installButton) {
                    return versionFileBox.getValue();
                }

                return null;
            }
        });

        java.util.Optional<ModrinthClient.ModVersionFile> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return null;
        }

        return result.get();
    }

    private Button createPremiumActionButton(String title, String subtitle, String icon) {
        Button btn = new Button(icon + "  " + title + "\n" + subtitle);
        btn.getStyleClass().add("premium-action-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(76);
        return btn;
    }

    private String formatVersionFileLabel(ModrinthClient.ModVersionFile file) {
        if (file == null) {
            return "";
        }

        String versionName = file.versionId == null || file.versionId.trim().isEmpty()
                ? "Versión"
                : file.versionId;

        return versionName + " · " + file.filename;
    }

    private Button createMinimalActionButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("minimal-action-button");
        return btn;
    }

    private void showToast(String message, String type) {
        if (toastContainer == null) {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
            return;
        }

        final Label toast = new Label(message);
        toast.getStyleClass().add("toast");

        if ("success".equalsIgnoreCase(type)) {
            toast.getStyleClass().add("toast-success");
        } else if ("error".equalsIgnoreCase(type)) {
            toast.getStyleClass().add("toast-error");
        } else if ("warning".equalsIgnoreCase(type)) {
            toast.getStyleClass().add("toast-warning");
        } else {
            toast.getStyleClass().add("toast-info");
        }

        toast.setWrapText(true);
        toast.setMaxWidth(360);

        toastContainer.getChildren().add(toast);

        javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.5));

        delay.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                toastContainer.getChildren().remove(toast);
            }
        });

        delay.play();
    }

    private File getCurrentCustomClientJar() {
        Instance instance = getCurrentInstance();

        if (instance == null || instance.customClientJarPath == null || instance.customClientJarPath.trim().isEmpty()) {
            return null;
        }

        File file = new File(instance.customClientJarPath);

        if (!file.exists() || !file.isFile()) {
            return null;
        }

        return file;
    }

    private File chooseJarFile(Stage owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Archive", "*.jar")
        );

        return chooser.showOpenDialog(owner);
    }

    private File importCustomClientJarToInstance(Instance instance, File sourceJar) throws Exception {
        if (instance == null) {
            throw new Exception("No hay instancia seleccionada.");
        }

        if (sourceJar == null || !sourceJar.exists() || !sourceJar.isFile()) {
            throw new Exception("El .jar seleccionado no existe.");
        }

        File clientDir = new File(InstanceManager.getGameDir(instance), "client");

        if (!clientDir.exists()) {
            clientDir.mkdirs();
        }

        String safeName = sourceJar.getName().replaceAll("[\\\\/:*?\"<>|]", "_");

        if (!safeName.toLowerCase().endsWith(".jar")) {
            safeName += ".jar";
        }

        File target = new File(clientDir, safeName);

        Files.copy(
                sourceJar.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        return target;
    }

    private String getCustomClientLabel(Instance instance) {
        if (instance == null || instance.customClientJarPath == null || instance.customClientJarPath.trim().isEmpty()) {
            return "No hay cliente personalizado asignado. Se usará el cliente normal de Minecraft.";
        }

        File file = new File(instance.customClientJarPath);

        if (!file.exists()) {
            return "Cliente personalizado configurado, pero el archivo no existe:\n" + instance.customClientJarPath;
        }

        return file.getName() + "\n" + file.getAbsolutePath();
    }

    private void openEditInstanceSafely() {
        try {
            Instance instance = getCurrentInstance();

            if (instance == null) {
                statusLabel.setText("No hay instancia seleccionada.");
                showToast("No hay instancia seleccionada", "warning");
                return;
            }

            showEditInstanceDialog();
        } catch (Exception ex) {
            ex.printStackTrace();

            String msg = ex.getMessage() == null ? "Error desconocido" : ex.getMessage();

            if (statusLabel != null) {
                statusLabel.setText("Error abriendo editor de instancia: " + msg);
            }

            showToast("Error abriendo editor de instancia", "error");
        }
    }

    private void installSelectedModpackVersion(final ModrinthClient.ModResult selected,
                                               final ModrinthClient.ModVersionFile selectedVersionFile,
                                               final Label status,
                                               final ProgressBar progressBar) {
        status.setText("Descargando modpack: " + selected.title + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                File temp = null;

                try {
                    File tempDir = new File(getLauncherDataDir(), "tmp-modpacks");
                    tempDir.mkdirs();

                    String ext = selectedVersionFile.filename.toLowerCase().endsWith(".mrpack") ? ".mrpack" : ".zip";
                    temp = new File(tempDir, safeModFileName(selectedVersionFile.filename));

                    downloadFileWithProgress(
                            selectedVersionFile.url,
                            temp,
                            status,
                            progressBar,
                            selected.title
                    );

                    final Instance imported = ModpackManager.importModpack(
                            temp,
                            getCurseForgeApiKey()
                    );

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            instances.add(imported);
                            refreshInstanceViews();
                            selectInstance(imported);
                            loadVersions();

                            progressBar.setVisible(false);
                            progressBar.setProgress(0);

                            status.setText("✅ Modpack importado como instancia: " + imported.name);
                            showToast("Modpack importado: " + imported.name, "success");
                        }
                    });
                } catch (final Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisible(false);
                            progressBar.setProgress(0);

                            status.setText("Error importando modpack: " + ex.getMessage());
                            showToast("Error importando modpack", "error");
                            ex.printStackTrace();
                        }
                    });
                }
            }
        }, "Install-Modpack");

        t.setDaemon(true);
        t.start();
    }

    private void setupModpackDragAndDrop(Scene scene) {
        scene.setOnDragOver(new EventHandler<javafx.scene.input.DragEvent>() {
            @Override
            public void handle(javafx.scene.input.DragEvent event) {
                if (event.getDragboard().hasFiles()) {
                    for (File file : event.getDragboard().getFiles()) {
                        String lower = file.getName().toLowerCase();

                        if (lower.endsWith(".mrpack") || lower.endsWith(".zip")) {
                            event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                            break;
                        }
                    }
                }

                event.consume();
            }
        });

        scene.setOnDragEntered(new EventHandler<javafx.scene.input.DragEvent>() {
            @Override
            public void handle(javafx.scene.input.DragEvent event) {
                if (dropOverlay != null && event.getDragboard().hasFiles()) {
                    dropOverlay.setVisible(true);
                }

                event.consume();
            }
        });

        scene.setOnDragExited(new EventHandler<javafx.scene.input.DragEvent>() {
            @Override
            public void handle(javafx.scene.input.DragEvent event) {
                if (dropOverlay != null) {
                    dropOverlay.setVisible(false);
                }

                event.consume();
            }
        });

        scene.setOnDragDropped(new EventHandler<javafx.scene.input.DragEvent>() {
            @Override
            public void handle(javafx.scene.input.DragEvent event) {
                boolean success = false;

                if (dropOverlay != null) {
                    dropOverlay.setVisible(false);
                }

                if (event.getDragboard().hasFiles()) {
                    for (File file : event.getDragboard().getFiles()) {
                        String lower = file.getName().toLowerCase();

                        if (lower.endsWith(".mrpack") || lower.endsWith(".zip")) {
                            importDroppedModpack(file);
                            success = true;
                            break;
                        }
                    }
                }

                event.setDropCompleted(success);
                event.consume();
            }
        });
    }

    private void importDroppedModpack(final File file) {
        if (file == null) {
            return;
        }

        showToast("Importando modpack...", "info");
        statusLabel.setText("Importando modpack: " + file.getName());
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Instance> task = new Task<Instance>() {
            @Override
            protected Instance call() throws Exception {
                return ModpackManager.importModpack(file, getCurseForgeApiKey());
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Instance instance = task.getValue();

                instances.add(instance);
                refreshInstanceViews();
                selectInstance(instance);
                loadVersions();

                statusLabel.setText("✅ Modpack importado: " + instance.name);
                showToast("Modpack importado: " + instance.name, "success");
            }
        });

        task.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressBar.setVisible(false);

                Throwable ex = task.getException();

                String msg = ex == null || ex.getMessage() == null ? "Error desconocido" : ex.getMessage();

                statusLabel.setText("❌ Error importando modpack: " + msg);
                showToast("Error importando modpack", "error");

                if (ex != null) {
                    ex.printStackTrace();
                }
            }
        });

        Thread t = new Thread(task, "Import-Dropped-Modpack");
        t.setDaemon(true);
        t.start();
    }

    private String getCurrentJvmArgs() {
        Instance instance = getCurrentInstance();

        if (instance == null || instance.jvmArgs == null) {
            return "";
        }

        return instance.jvmArgs.trim();
    }

    private void writeInstanceLaunchLog(String version, String username, int ram, File gameDir) {
        try {
            File logsDir = new File(gameDir, "logs");

            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            File log = new File(logsDir, "launcher-instance.log");

            PrintWriter writer = new PrintWriter(new FileWriter(log, true));

            try {
                writer.println("=== Launch at " + java.time.LocalDateTime.now() + " ===");
                writer.println("Instance: " + (getCurrentInstance() == null ? "Unknown" : getCurrentInstance().name));
                writer.println("Version: " + version);
                writer.println("Username: " + username);
                writer.println("RAM: " + ram + " GB");
                writer.println("GameDir: " + gameDir.getAbsolutePath());
                writer.println();
            } finally {
                writer.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void applyVersionFilter(String query, String filter) {
        if (allVersions == null || versionBox == null) {
            return;
        }

        String q = query == null ? "" : query.toLowerCase().trim();
        String f = filter == null ? "Todas" : filter;

        List<VersionEntry> filtered = new ArrayList<VersionEntry>();

        for (VersionEntry v : allVersions) {
            if (v == null || v.id == null) {
                continue;
            }

            String id = v.id;
            String lower = id.toLowerCase();

            if (!q.isEmpty() && !lower.contains(q)) {
                continue;
            }

            if ("Fabric".equals(f) && !lower.contains("fabric")) {
                continue;
            }

            if ("Vanilla".equals(f) && lower.contains("fabric")) {
                continue;
            }

            if ("Instaladas".equals(f) && !isVersionInstalled(id)) {
                continue;
            }

            filtered.add(v);
        }

        versionBox.getItems().setAll(filtered);

        if (!filtered.isEmpty()) {
            versionBox.getSelectionModel().selectFirst();
        }
    }

    private boolean isVersionInstalled(String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) {
            return false;
        }

        File json = new File(
                VersionManager.MC_DIR,
                "versions/" + versionId + "/" + versionId + ".json"
        );

        return json.exists();
    }

    private File installSelectedContentFile(ModrinthClient.ModResult selected,
                                            ModrinthClient.ModVersionFile selectedFile,
                                            Label statusLabel,
                                            ProgressBar progressBar) throws Exception {
        if (selected == null) {
            throw new Exception("No hay contenido seleccionado.");
        }

        if (selectedFile == null) {
            throw new Exception("No hay versión seleccionada.");
        }

        String type = selected.projectType == null || selected.projectType.trim().isEmpty()
                ? "mod"
                : selected.projectType;

        if (isContentInstalled(selected.projectId, type) || isLogicalContentInstalled(selected)) {
            File existing = findInstalledContentFile(selected.projectId, type);

            if (existing != null && existing.exists()) {
                return existing;
            }

            throw new Exception("Este contenido o un equivalente ya está instalado.");
        }

        File targetDir = getContentDirectory(type);
        targetDir.mkdirs();

        File targetFile = new File(targetDir, safeModFileName(selectedFile.filename));

        if (targetFile.exists() && targetFile.length() > 0) {
            markContentInstalled(selected.projectId, type, selected.title, targetFile);
            markLogicalContentInstalled(selected, targetFile);
            return targetFile;
        }

        downloadFileWithProgress(
                selectedFile.url,
                targetFile,
                statusLabel,
                progressBar,
                selected.title
        );

        markContentInstalled(selected.projectId, type, selected.title, targetFile);
        markLogicalContentInstalled(selected, targetFile);

        return targetFile;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
