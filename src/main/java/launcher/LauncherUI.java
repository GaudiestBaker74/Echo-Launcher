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

    private static final Map<String, Image> iconMemoryCache = new ConcurrentHashMap<String, Image>();

    private static final ExecutorService iconExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Modrinth-Icon-Loader");
            thread.setDaemon(true);
            return thread;
        }
    });

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

            // Instances
// Instances - Prism style cards
// Instances - Prism style cards without ListView
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

            topBar.getChildren().addAll(
                    brandBox,
                    topSpacer,
                    topUserLabel,
                    topInstanceBtn,
                    topModsBtn,
                    topSearchBtn,
                    topGraphicsBtn,
                    topSkinsBtn
            );

// ================= HERO CARD =================
            BorderPane heroCard = new BorderPane();
            heroCard.getStyleClass().add("hero-card");
            heroCard.setMaxWidth(Double.MAX_VALUE);

            HBox heroLeft = new HBox(16);
            heroLeft.setAlignment(Pos.CENTER_LEFT);

            heroInstanceIconLabel = new Label("🌱");
            heroInstanceIconLabel.getStyleClass().add("hero-icon");

            VBox heroTextBox = new VBox(5);

            Label heroSmallTitle = new Label("Instancia actual");
            heroSmallTitle.getStyleClass().add("hero-small-title");

            heroInstanceNameLabel = new Label("Principal");
            heroInstanceNameLabel.getStyleClass().add("hero-title");

            heroInstanceMetaLabel = new Label("Sin versión · 2 GB RAM");
            heroInstanceMetaLabel.getStyleClass().add("hero-meta");

            heroInstanceModsLabel = new Label("0 mods");
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

            heroLeft.getChildren().addAll(heroInstanceIconLabel, heroTextBox);

            Button playButton = new Button("Jugar");
            playButton.getStyleClass().add("hero-play-button");
            playButton.setPrefWidth(170);
            playButton.setPrefHeight(56);
            playButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    launchGame();
                }
            });

            heroCard.setLeft(heroLeft);
            heroCard.setRight(playButton);
            BorderPane.setAlignment(playButton, Pos.CENTER_RIGHT);

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

            final TextField searchField = new TextField();
            searchField.setPromptText("Buscar versión...");
            searchField.setPrefWidth(180);
            searchField.getStyleClass().add("dashboard-search");

            versionHead.getChildren().addAll(versionLabel, versionSpacer, searchField);

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

// ================= QUICK ACTIONS CARD =================
            HBox quickActions = new HBox(10);
            quickActions.setAlignment(Pos.CENTER_LEFT);
            quickActions.getStyleClass().add("quick-actions-bar");

            Button quickModsBtn = new Button("Mods");
            quickModsBtn.getStyleClass().add("quick-action-button");

            Button quickSearchBtn = new Button("Buscar contenido");
            quickSearchBtn.getStyleClass().add("quick-action-button");

            Button quickGraphicsBtn = new Button("Pack gráfico");
            quickGraphicsBtn.getStyleClass().add("quick-action-button");

            Button quickEditBtn = new Button("Editar instancia");
            quickEditBtn.getStyleClass().add("quick-action-button");

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
                    showEditInstanceDialog();
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
                    statusCard,
                    quickActions,
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
        Scene scene = new Scene(root, 900, 600);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load style.css");
        }

        stage.setScene(scene);
            stage.setTitle("Minecraft Launcher");
            stage.setMinWidth(980);
            stage.setMinHeight(620);
        stage.show();

        // Listeners for functionality
        searchField.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> obs, String oldV, final String newV) {
                if (allVersions == null)
                    return;
                List<VersionEntry> filtered = new ArrayList<VersionEntry>();
                for (VersionEntry v : allVersions) {
                    if (v.id.toLowerCase().contains(newV.toLowerCase())) {
                        filtered.add(v);
                    }
                }
                versionBox.getItems().setAll(filtered);
                if (!filtered.isEmpty()) {
                    versionBox.getSelectionModel().selectFirst();
                }
            }
        });

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
                fabricBtn.setDisable(true);
                statusLabel.setText("Instalando Fabric para " + v.id + "...");
                progressBar.setVisible(true);
                progressBar.setProgress(-1);

                FabricManager.install(v, new FabricManager.LauncherCallback() {
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
        });

// Mucho más abajo, al final de start:
            loadSettings();
            loadInstancesIntoCombo();
            loadProfilesIntoCombo();
            loadVersions();
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de inicio");
            alert.setHeaderText("Error al iniciar el launcher");
            alert.setContentText(e.getMessage());
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
                dialog.close();
                showEditInstanceDialog();
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
                new Label("Usuario"),
                usernameEditField,
                new Label("Versión"),
                versionEditBox,
                typeLabel
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

        javaCard.getChildren().addAll(javaTitle, ramHeader, ramEditSlider, javaHint);

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

        folderRow1.getChildren().addAll(openRootBtn, openModsBtn, openResourcepacksBtn);
        folderRow2.getChildren().addAll(openShaderpacksBtn, openConfigBtn);

        openRootBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                openInstanceSubFolder(instance, "");
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
                    instance.username = usernameEditField.getText() == null ? "" : usernameEditField.getText().trim();
                    instance.version = selectedVersion.id;
                    instance.type = detectProfileType(selectedVersion.id);
                    instance.ram = (int) ramEditSlider.getValue();
                    instance.notes = notesArea.getText() == null ? "" : notesArea.getText();

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

        content.getChildren().addAll(generalCard, javaCard, notesCard, foldersCard, footer);

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
        if (instance == null || instance.icon == null) {
            return "🌱";
        }

        String icon = instance.icon.toLowerCase();

        if (icon.contains("sword") || icon.contains("pvp")) {
            return "⚔";
        }

        if (icon.contains("shader") || icon.contains("star")) {
            return "✨";
        }

        if (icon.contains("tech") || icon.contains("gear")) {
            return "⚙";
        }

        if (icon.contains("test") || icon.contains("lab")) {
            return "🧪";
        }

        if (icon.contains("grass")) {
            return "🌱";
        }

        return "🎮";
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
    }

    private Instance getCurrentInstance() {
        if (selectedInstance != null) {
            return selectedInstance;
        }

        if (instanceBox != null && instanceBox.getValue() != null) {
            return instanceBox.getValue();
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

        InstalledModInfo(File file) {
            this.file = file;
            this.name = file.getName();
            this.description = "Sin descripción disponible.";
            this.modId = "";
            this.version = "";
            this.enabled = file.getName().toLowerCase().endsWith(".jar");
            this.icon = null;
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
                                                            Label statusLabel) throws Exception {
        java.util.Set<String> installing = new java.util.HashSet<String>();
        return installContentRecursive(selected, mcVersion, statusLabel, installing, 0);
    }

    private File installContentRecursive(ModrinthClient.ModResult selected,
                                         String mcVersion,
                                         Label statusLabel,
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

                installContentRecursive(depResult, mcVersion, statusLabel, installing, depth + 1);
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
            installing.remove(projectId);
            return targetFile;
        }

        downloadModFile(fileData.url, targetFile);

        markContentInstalled(projectId, type, selected.title, targetFile);

        installing.remove(projectId);

        return targetFile;
    }

    private InstalledModInfo readInstalledModInfo(File file) {
        InstalledModInfo info = new InstalledModInfo(file);

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
        dialog.setTitle("Mods instalados");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Mods");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Label subtitle = new Label("Activa, desactiva y administra tus mods instalados.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(14);
        content.setPadding(new Insets(0, 24, 20, 24));

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

        final ListView<InstalledModInfo> installedList = new ListView<InstalledModInfo>();
        installedList.getStyleClass().add("list-view");
        VBox.setVgrow(installedList, Priority.ALWAYS);

        installedList.setCellFactory(new javafx.util.Callback<ListView<InstalledModInfo>, ListCell<InstalledModInfo>>() {
            @Override
            public ListCell<InstalledModInfo> call(ListView<InstalledModInfo> listView) {
                return new ListCell<InstalledModInfo>() {
                    @Override
                    protected void updateItem(InstalledModInfo item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }

                        HBox row = new HBox(14);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(12));
                        row.getStyleClass().add("installed-mod-row");

                        StackPane iconBox = new StackPane();
                        iconBox.getStyleClass().add("installed-mod-icon-box");
                        iconBox.setMinSize(58, 58);
                        iconBox.setPrefSize(58, 58);
                        iconBox.setMaxSize(58, 58);

                        if (item.icon != null && !item.icon.isError()) {
                            ImageView iconView = new ImageView(item.icon);
                            iconView.setFitWidth(50);
                            iconView.setFitHeight(50);
                            iconView.setPreserveRatio(true);
                            iconView.setSmooth(true);
                            iconBox.getChildren().add(iconView);
                        } else {
                            Label fallback = new Label("📦");
                            fallback.getStyleClass().add("installed-mod-fallback-icon");
                            iconBox.getChildren().add(fallback);
                        }

                        VBox infoBox = new VBox(6);
                        HBox.setHgrow(infoBox, Priority.ALWAYS);

                        HBox titleRow = new HBox(8);
                        titleRow.setAlignment(Pos.CENTER_LEFT);

                        Label nameLabel = new Label(item.name);
                        nameLabel.getStyleClass().add("installed-mod-name");
                        nameLabel.setMaxWidth(360);

                        Region titleSpacer = new Region();
                        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

                        Label stateBadge = new Label(item.enabled ? "Activo" : "Desactivado");
                        stateBadge.getStyleClass().add(item.enabled ? "mod-state-active" : "mod-state-disabled");

                        titleRow.getChildren().addAll(nameLabel, titleSpacer, stateBadge);

                        Label descriptionLabel = new Label(item.description);
                        descriptionLabel.getStyleClass().add("installed-mod-description");
                        descriptionLabel.setWrapText(true);
                        descriptionLabel.setMaxWidth(520);

                        Label metaLabel = new Label(
                                item.file.getName() + " · " + formatFileSize(item.file.length())
                        );
                        metaLabel.getStyleClass().add("installed-mod-meta");
                        metaLabel.setWrapText(true);

                        infoBox.getChildren().addAll(titleRow, descriptionLabel, metaLabel);

                        row.getChildren().addAll(iconBox, infoBox);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        final Label status = new Label("Cargando mods...");
        status.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        status.setWrapText(true);

        content.getChildren().addAll(topBar, installedList, status);

        installedList.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<InstalledModInfo>() {
                    @Override
                    public void changed(ObservableValue<? extends InstalledModInfo> observable,
                                        InstalledModInfo oldValue,
                                        InstalledModInfo newValue) {
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
                refreshInstalledModsPanel(installedList, status);
            }
        });

        openFolderBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File modsDir = getModsDirectory();
                    modsDir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(modsDir);
                    }
                } catch (Exception ex) {
                    status.setText("No se pudo abrir la carpeta: " + ex.getMessage());
                }
            }
        });

        toggleBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                InstalledModInfo selected = installedList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                try {
                    File newFile = toggleModEnabled(selected.file);
                    status.setText((selected.enabled ? "Mod desactivado: " : "Mod activado: ") + newFile.getName());
                    refreshInstalledModsPanel(installedList, status);
                } catch (Exception ex) {
                    status.setText("No se pudo cambiar el estado del mod: " + ex.getMessage());
                }
            }
        });

        deleteBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final InstalledModInfo selected = installedList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Eliminar mod");
                confirm.setHeaderText("¿Eliminar este mod?");
                confirm.setContentText(selected.name + "\n\nArchivo: " + selected.file.getName());

                java.util.Optional<ButtonType> result = confirm.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    boolean deleted = selected.file.delete();

                    if (deleted) {
                        removeMarkersForContentFile(selected.file);
                        status.setText("Mod eliminado: " + selected.name);
                    } else {
                        status.setText("No se pudo eliminar: " + selected.file.getName());
                    }

                    refreshInstalledModsPanel(installedList, status);
                }
            }
        });

        root.setTop(header);
        root.setCenter(content);

        refreshInstalledModsPanel(installedList, status);

        Scene scene = new Scene(root, 760, 600);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(540);
        dialog.show();
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

        final ComboBox<String> typeBox = new ComboBox<String>();
        typeBox.getItems().addAll("Mods", "Texturas", "Shaders");
        typeBox.getSelectionModel().selectFirst();
        typeBox.setPrefWidth(135);

        final TextField searchField = new TextField();
        searchField.setPromptText("Buscar en Modrinth...");
        searchField.getStyleClass().add("text-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        final Button searchBtn = new Button("Buscar");
        searchBtn.getStyleClass().add("button");

        searchBox.getChildren().addAll(typeBox, searchField, searchBtn);

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

                        top.getChildren().addAll(nameLabel, topSpacer, typeLabel);

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

        content.getChildren().addAll(searchBox, resultsList, status, bottomBar);

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

                if (query.isEmpty()) {
                    loadPopularContent(typeBox, resultsList, searchBtn, installBtn, status);
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
                loadPopularContent(typeBox, resultsList, searchBtn, installBtn, status);
            }
        });

        installBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final ModrinthClient.ModResult selected = resultsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                if (isContentInstalled(selected.projectId, selected.projectType)) {
                    installBtn.setText("Ya instalado");
                    installBtn.setDisable(true);
                    status.setText("Este contenido ya está instalado.");
                    return;
                }

                final String mcVersion = getCurrentMinecraftVersionForMods();

                installBtn.setDisable(true);
                searchBtn.setDisable(true);
                status.setText("Instalando " + selected.title + "...");

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final File installedFile = installContentFromModrinthWithDependencies(selected, mcVersion, status);

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchBtn.setDisable(false);
                                    installBtn.setText("Ya instalado");
                                    installBtn.setDisable(true);
                                    status.setText("Instalado correctamente junto con sus dependencias: " + installedFile.getName());
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
                                        status.setText("Error instalando: " + msg);
                                    }
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

        loadPopularContent(typeBox, resultsList, searchBtn, installBtn, status);
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

        return "mod";
    }

    private String getContentTypeLabel(String type) {
        if ("resourcepack".equalsIgnoreCase(type)) {
            return "Textura";
        }

        if ("shader".equalsIgnoreCase(type)) {
            return "Shader";
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

        if (isContentInstalled(selected.projectId, selected.projectType)) {
            installBtn.setText("Ya instalado");
            installBtn.setDisable(true);

            File installed = findInstalledContentFile(selected.projectId, selected.projectType);

            if (installed != null && installed.exists()) {
                statusLabel.setText("Ya instalado: " + installed.getName());
            } else {
                statusLabel.setText("Este contenido ya está instalado.");
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
        File dir = new File(System.getProperty("user.home"), ".minecraft-launcher/icon-cache");

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

    private void showModDownloaderDialog() {
        showModDownloaderDialog(false);
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

    private void showModDownloaderDialog(final boolean openInstalledTab) {
        final Stage dialog = new Stage();
        dialog.setTitle("Gestor de Mods");
        dialog.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f6f8fb;");

        VBox header = new VBox(6);
        header.setPadding(new Insets(22, 24, 14, 24));

        Label title = new Label("Gestor de Mods");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        String selectedVersion = "ninguna";
        if (versionBox.getValue() != null) {
            selectedVersion = extractMinecraftVersionForMods(versionBox.getValue().id);
        }

        Label subtitle = new Label("Busca, instala y administra mods para Minecraft " + selectedVersion + ".");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");
        subtitle.setWrapText(true);

        header.getChildren().addAll(title, subtitle);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: transparent;");

        /*
         * TAB BUSCAR MODS
         */
        VBox searchRoot = new VBox(14);
        searchRoot.setPadding(new Insets(18));
        searchRoot.setStyle("-fx-background-color: #f6f8fb;");

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        final TextField searchField = new TextField();
        searchField.setPromptText("Buscar mods en Modrinth, ej. sodium, iris, xaero...");
        searchField.getStyleClass().add("text-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        final Button searchBtn = new Button("Buscar");
        searchBtn.getStyleClass().add("button");

        searchBox.getChildren().addAll(searchField, searchBtn);

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

                        VBox card = new VBox(6);
                        card.setPadding(new Insets(10));
                        card.setStyle(
                                "-fx-background-color: transparent;" +
                                        "-fx-background-radius: 14px;"
                        );

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);

                        Label nameLabel = new Label(item.title);
                        nameLabel.setStyle(
                                "-fx-font-size: 15px;" +
                                        "-fx-font-weight: 800;" +
                                        "-fx-text-fill: #111827;"
                        );

                        Label slugLabel = new Label(item.slug);
                        slugLabel.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-font-weight: 700;" +
                                        "-fx-text-fill: #1d4ed8;" +
                                        "-fx-background-color: #dbeafe;" +
                                        "-fx-background-radius: 999px;" +
                                        "-fx-padding: 4px 8px;"
                        );

                        top.getChildren().addAll(nameLabel, slugLabel);

                        Label descLabel = new Label(item.description == null ? "" : item.description);
                        descLabel.setWrapText(true);
                        descLabel.setMaxWidth(610);
                        descLabel.setStyle(
                                "-fx-font-size: 12px;" +
                                        "-fx-text-fill: #6b7280;"
                        );

                        Label idLabel = new Label("Project ID: " + item.projectId);
                        idLabel.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-text-fill: #9ca3af;"
                        );

                        card.getChildren().addAll(top, descLabel, idLabel);

                        setText(null);
                        setGraphic(card);
                    }
                };
            }
        });

        HBox actionBar = new HBox(10);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        final Button installBtn = new Button("Instalar seleccionado");
        installBtn.getStyleClass().add("launch-button");
        installBtn.setDisable(true);

        final Button openModsBtn = new Button("Abrir carpeta mods");
        openModsBtn.getStyleClass().add("secondary-button");

        final Label searchStatus = new Label("Busca un mod para empezar.");
        searchStatus.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        searchStatus.setWrapText(true);

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        actionBar.getChildren().addAll(installBtn, openModsBtn, actionSpacer);

        VBox searchBottom = new VBox(8);
        searchBottom.getChildren().addAll(searchStatus, actionBar);

        searchRoot.getChildren().addAll(searchBox, resultsList, searchBottom);

        /*
         * TAB INSTALADOS
         */
        VBox installedRoot = new VBox(14);
        installedRoot.setPadding(new Insets(18));
        installedRoot.setStyle("-fx-background-color: #f6f8fb;");

        HBox installedTop = new HBox(10);
        installedTop.setAlignment(Pos.CENTER_LEFT);

        Label installedTitle = new Label("Mods instalados");
        installedTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #111827;");

        Region installedSpacer = new Region();
        HBox.setHgrow(installedSpacer, Priority.ALWAYS);

        final Button refreshInstalledBtn = new Button("Actualizar");
        refreshInstalledBtn.getStyleClass().add("secondary-button");

        final Button deleteModBtn = new Button("Eliminar");
        deleteModBtn.getStyleClass().add("secondary-button");
        deleteModBtn.setDisable(true);

        installedTop.getChildren().addAll(installedTitle, installedSpacer, refreshInstalledBtn, deleteModBtn);

        final ListView<File> installedList = new ListView<File>();
        installedList.getStyleClass().add("list-view");
        VBox.setVgrow(installedList, Priority.ALWAYS);

        installedList.setCellFactory(new javafx.util.Callback<ListView<File>, ListCell<File>>() {
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

                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(10));

                        StackPane iconBox = new StackPane();
                        iconBox.getStyleClass().add("content-icon-box");
                        iconBox.setMinSize(54, 54);
                        iconBox.setPrefSize(54, 54);
                        iconBox.setMaxSize(54, 54);

                        Label fileIcon = new Label(item.getName().toLowerCase().endsWith(".disabled") ? "⏸" : "📦");
                        fileIcon.getStyleClass().add("content-icon-fallback");
                        iconBox.getChildren().add(fileIcon);

                        VBox infoBox = new VBox(6);
                        HBox.setHgrow(infoBox, Priority.ALWAYS);

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);

                        Label fileName = new Label(item.getName());
                        fileName.setStyle(
                                "-fx-font-size: 14px;" +
                                        "-fx-font-weight: 800;" +
                                        "-fx-text-fill: #111827;"
                        );

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Label stateBadge = new Label(item.getName().toLowerCase().endsWith(".disabled") ? "Desactivado" : "Activo");

                        if (item.getName().toLowerCase().endsWith(".disabled")) {
                            stateBadge.setStyle(
                                    "-fx-font-size: 11px;" +
                                            "-fx-font-weight: 800;" +
                                            "-fx-text-fill: #92400e;" +
                                            "-fx-background-color: #fef3c7;" +
                                            "-fx-background-radius: 999px;" +
                                            "-fx-padding: 4px 8px;"
                            );
                        } else {
                            stateBadge.setStyle(
                                    "-fx-font-size: 11px;" +
                                            "-fx-font-weight: 800;" +
                                            "-fx-text-fill: #166534;" +
                                            "-fx-background-color: #dcfce7;" +
                                            "-fx-background-radius: 999px;" +
                                            "-fx-padding: 4px 8px;"
                            );
                        }

                        top.getChildren().addAll(fileName, spacer, stateBadge);

                        Label meta = new Label(formatFileSize(item.length()) + " · " + item.getAbsolutePath());
                        meta.setStyle(
                                "-fx-font-size: 11px;" +
                                        "-fx-text-fill: #6b7280;"
                        );
                        meta.setWrapText(true);

                        infoBox.getChildren().addAll(top, meta);
                        row.getChildren().addAll(iconBox, infoBox);

                        setText(null);
                        setGraphic(row);
                    }
                };
            }
        });

        final Label installedStatus = new Label("Mods cargados desde la carpeta mods.");
        installedStatus.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        installedStatus.setWrapText(true);

        installedRoot.getChildren().addAll(installedTop, installedList, installedStatus);

        Tab searchTab = new Tab("Buscar");
        searchTab.setContent(searchRoot);

        Tab installedTab = new Tab("Instalados");
        installedTab.setContent(installedRoot);

        tabs.getTabs().addAll(searchTab, installedTab);

        root.setTop(header);
        root.setCenter(tabs);

        /*
         * EVENTOS
         */
        resultsList.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<ModrinthClient.ModResult>() {
                    @Override
                    public void changed(ObservableValue<? extends ModrinthClient.ModResult> observable,
                                        ModrinthClient.ModResult oldValue,
                                        ModrinthClient.ModResult newValue) {
                        updateInstallButtonState(newValue, installBtn, searchStatus);
                    }
                }
        );

        installedList.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<File>() {
                    @Override
                    public void changed(ObservableValue<? extends File> observable, File oldValue, File newValue) {
                        deleteModBtn.setDisable(newValue == null);
                    }
                }
        );

        EventHandler<ActionEvent> doSearch = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final String query = searchField.getText() == null ? "" : searchField.getText().trim();

                if (query.isEmpty()) {
                    searchStatus.setText("Escribe el nombre de un mod.");
                    return;
                }

                searchBtn.setDisable(true);
                installBtn.setDisable(true);
                resultsList.getItems().clear();
                searchStatus.setText("Buscando \"" + query + "\" en Modrinth...");

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<ModrinthClient.ModResult> results = ModrinthClient.searchMods(query);

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    resultsList.getItems().setAll(results);
                                    searchBtn.setDisable(false);

                                    if (results.isEmpty()) {
                                        searchStatus.setText("No se encontraron resultados.");
                                    } else {
                                        searchStatus.setText("Se encontraron " + results.size() + " resultados.");
                                    }
                                }
                            });
                        } catch (final Exception ex) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchBtn.setDisable(false);
                                    searchStatus.setText("Error buscando mods: " + ex.getMessage());
                                }
                            });
                        }
                    }
                }, "Modrinth-Search");

                t.setDaemon(true);
                t.start();
            }
        };

        searchBtn.setOnAction(doSearch);
        searchField.setOnAction(doSearch);

        installBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final ModrinthClient.ModResult selected = resultsList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                if (isModrinthProjectInstalled(selected.projectId)) {
                    installBtn.setText("Ya instalado");
                    installBtn.setDisable(true);
                    searchStatus.setText("Este mod ya está instalado.");
                    return;
                }

                final String mcVersion = getCurrentMinecraftVersionForMods();

                installBtn.setDisable(true);
                searchBtn.setDisable(true);
                searchStatus.setText("Preparando instalación de " + selected.title + " para Minecraft " + mcVersion + "...");

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final File installedFile = installModFromModrinth(selected, mcVersion);

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    installBtn.setText("Ya instalado");
                                    installBtn.setDisable(true);
                                    searchBtn.setDisable(false);

                                    searchStatus.setText("Instalado correctamente: " + installedFile.getName());

                                    refreshInstalledMods(installedList, installedStatus);
                                    tabs.getSelectionModel().select(installedTab);
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
                                        searchStatus.setText(msg);
                                    } else {
                                        installBtn.setText("Instalar seleccionado");
                                        installBtn.setDisable(false);
                                        searchStatus.setText("Error instalando mod: " + msg);
                                    }
                                }
                            });
                        }
                    }
                }, "Modrinth-Install");

                t.setDaemon(true);
                t.start();
            }
        });

        openModsBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    File modsDir = getModsDirectory();
                    modsDir.mkdirs();

                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(modsDir);
                    }
                } catch (Exception ex) {
                    searchStatus.setText("No se pudo abrir la carpeta mods: " + ex.getMessage());
                }
            }
        });

        refreshInstalledBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                refreshInstalledMods(installedList, installedStatus);
            }
        });

        deleteModBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final File selected = installedList.getSelectionModel().getSelectedItem();

                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Eliminar mod");
                confirm.setHeaderText("¿Eliminar este mod?");
                confirm.setContentText(selected.getName());

                java.util.Optional<ButtonType> result = confirm.showAndWait();

                if (result.isPresent() && result.get() == ButtonType.OK) {
                    boolean deleted = selected.delete();

                    if (deleted) {
                        removeMarkersForModFile(selected);
                        installedStatus.setText("Mod eliminado: " + selected.getName());
                    } else {
                        installedStatus.setText("No se pudo eliminar: " + selected.getName());
                    }

                    refreshInstalledMods(installedList, installedStatus);
                }
            }
        });

        refreshInstalledMods(installedList, installedStatus);

        Scene scene = new Scene(root, 760, 620);

        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(560);
        dialog.show();
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
        File dir = new File(VersionManager.MC_DIR, ".launcher-content");

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

        if (installedFile != null && installedFile.exists()) {
            return true;
        }

        marker.delete();
        return false;
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
                        Desktop.getDesktop().open(folder);
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

        saveSettings();

        try {
            saveSelectedInstance();
        } catch (Exception ex) {
            System.err.println("[Instance] No se pudo guardar instancia antes de jugar: " + ex.getMessage());
        }

        final File gameDir = getCurrentInstanceGameDir();

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
                MinecraftLauncher.launch(entry.id, username, ram, gameDir);
                return null;
            }
        };

        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                statusLabel.setText("✅ Juego en ejecución.");
                progressBar.setVisible(false);
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
                    String msg = ex == null || ex.getMessage() == null ? "Error desconocido" : ex.getMessage();
                    statusLabel.setText("❌ Error iniciando Minecraft: " + msg);

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

    public static void main(String[] args) {
        launch(args);
    }
}
