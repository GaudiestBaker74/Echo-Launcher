package launcher;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.application.ConditionalFeature;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.animation.AnimationTimer;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;

import java.io.File;
import java.net.URLEncoder;

public class SkinViewer3D {

    private static final double MODEL_SCALE = 1.55;

    private static class Rect {
        double x;
        double y;
        double w;
        double h;

        Rect(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static class CuboidUV {
        Rect front;
        Rect back;
        Rect left;
        Rect right;
        Rect top;
        Rect bottom;

        CuboidUV(Rect front, Rect back, Rect left, Rect right, Rect top, Rect bottom) {
            this.front = front;
            this.back = back;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    public static void show(Window owner, String username, File skinFile, File capeFile) {
        if (!Platform.isSupported(ConditionalFeature.SCENE3D)) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Visor 3D");
            alert.setHeaderText("JavaFX 3D no está disponible");
            alert.setContentText("Tu instalación de JavaFX no soporta Scene3D en este equipo.");
            alert.showAndWait();
            return;
        }

        try {
            Image skin = loadSkin(username, skinFile);
            Image cape = loadCape(capeFile);

            Stage dialog = new Stage();
            dialog.setTitle("Visor 3D de Skin");
            dialog.initModality(Modality.APPLICATION_MODAL);

            if (owner != null) {
                dialog.initOwner(owner);
            }

            BorderPane root = new BorderPane();
            root.setStyle(
                    "-fx-background-color: #f6f8fb;"
            );

            VBox header = new VBox(4);
            header.setPadding(new Insets(20, 22, 12, 22));

            Label title = new Label("Visor 3D");
            title.setStyle(
                    "-fx-font-size: 24px;" +
                            "-fx-font-weight: 800;" +
                            "-fx-text-fill: #111827;"
            );

            Label subtitle = new Label("Arrastra para rotar · Ctrl + rueda para zoom");
            subtitle.setStyle(
                    "-fx-font-size: 13px;" +
                            "-fx-text-fill: #6b7280;"
            );

            header.getChildren().addAll(title, subtitle);

            Group player = createPlayerModel(skin, cape);

            Rotate rotateX = new Rotate(-8, Rotate.X_AXIS);
            Rotate rotateY = new Rotate(28, Rotate.Y_AXIS);
            Scale scale = new Scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

            final boolean[] autoRotate = new boolean[]{false};

            AnimationTimer autoRotateTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (autoRotate[0]) {
                        rotateY.setAngle(rotateY.getAngle() + 0.35);
                    }
                }
            };

            player.getTransforms().addAll(rotateX, rotateY, scale);

            Group world = new Group();

            AmbientLight ambient = new AmbientLight(Color.color(0.78, 0.78, 0.82));

            PointLight keyLight = new PointLight(Color.WHITE);
            keyLight.setTranslateX(-45);
            keyLight.setTranslateY(-55);
            keyLight.setTranslateZ(-75);

            PointLight fillLight = new PointLight(Color.color(0.65, 0.75, 1.0));
            fillLight.setTranslateX(55);
            fillLight.setTranslateY(25);
            fillLight.setTranslateZ(-35);

            world.getChildren().addAll(player, ambient, keyLight, fillLight);

            PerspectiveCamera camera = new PerspectiveCamera(true);
            camera.setNearClip(0.1);
            camera.setFarClip(1000);
            camera.setFieldOfView(35);
            camera.setTranslateZ(-82);
            camera.setTranslateY(0);

            SubScene subScene = new SubScene(
                    world,
                    500,
                    540,
                    true,
                    SceneAntialiasing.BALANCED
            );

            subScene.setFill(Color.web("#f6f8fb"));
            subScene.setCamera(camera);

            StackPane viewerCard = new StackPane(subScene);
            viewerCard.setPadding(new Insets(10));
            viewerCard.setPrefSize(520, 560);
            viewerCard.setMinSize(520, 560);
            viewerCard.setMaxSize(520, 560);
            viewerCard.setStyle(
                    "-fx-background-color: #ffffff;" +
                            "-fx-background-radius: 24px;" +
                            "-fx-border-color: #e5e7eb;" +
                            "-fx-border-width: 1px;" +
                            "-fx-border-radius: 24px;" +
                            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 24, 0.20, 0, 10);"
            );

            StackPane centerWrap = new StackPane(viewerCard);
            centerWrap.setPadding(new Insets(0, 22, 0, 22));
            StackPane.setAlignment(viewerCard, Pos.CENTER);

            setupMouseControls(subScene, rotateX, rotateY, camera);

            HBox footer = new HBox(10);
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.setPadding(new Insets(14, 22, 20, 22));

            Button resetBtn = new Button("Restablecer");
            resetBtn.getStyleClass().add("secondary-button");

            Button leftBtn = new Button("Girar izquierda");
            leftBtn.getStyleClass().add("secondary-button");

            Button rightBtn = new Button("Girar derecha");
            rightBtn.getStyleClass().add("secondary-button");

            Button closeBtn = new Button("Cerrar");
            closeBtn.getStyleClass().add("button");

            CheckBox autoRotateBox = new CheckBox("Auto rotar");
            autoRotateBox.setStyle("-fx-text-fill: #374151; -fx-font-weight: 700;");

            ComboBox<String> backgroundBox = new ComboBox<String>();
            backgroundBox.getItems().addAll("Claro", "Azul", "Oscuro");
            backgroundBox.getSelectionModel().selectFirst();
            backgroundBox.setPrefWidth(110);

            autoRotateBox.setOnAction(event -> {
                autoRotate[0] = autoRotateBox.isSelected();

                if (autoRotate[0]) {
                    autoRotateTimer.start();
                } else {
                    autoRotateTimer.stop();
                }
            });

            backgroundBox.setOnAction(event -> {
                String value = backgroundBox.getValue();

                if ("Oscuro".equals(value)) {
                    subScene.setFill(Color.web("#111827"));
                } else if ("Azul".equals(value)) {
                    subScene.setFill(Color.web("#eef2ff"));
                } else {
                    subScene.setFill(Color.web("#f6f8fb"));
                }
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            resetBtn.setOnAction(event -> {
                rotateX.setAngle(-8);
                rotateY.setAngle(28);
                camera.setTranslateZ(-82);
            });

            leftBtn.setOnAction(event -> rotateY.setAngle(rotateY.getAngle() - 35));
            rightBtn.setOnAction(event -> rotateY.setAngle(rotateY.getAngle() + 35));
            closeBtn.setOnAction(event -> dialog.close());

            footer.getChildren().addAll(
                    resetBtn,
                    leftBtn,
                    rightBtn,
                    autoRotateBox,
                    backgroundBox,
                    spacer,
                    closeBtn
            );

            root.setTop(header);
            root.setCenter(centerWrap);
            root.setBottom(footer);

            Scene scene = new Scene(root, 620, 720, true);

            try {
                scene.getStylesheets().add(SkinViewer3D.class.getResource("/style.css").toExternalForm());
            } catch (Exception ignored) {
            }

            dialog.setScene(scene);
            dialog.setMinWidth(560);
            dialog.setMinHeight(640);
            dialog.show();

        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Visor 3D");
            alert.setHeaderText("No se pudo cargar el visor 3D");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private static Image loadSkin(String username, File skinFile) throws Exception {
        String source;

        if (skinFile != null && skinFile.exists()) {
            source = skinFile.toURI().toString();
        } else {
            String name = username == null ? "" : username.trim();

            if (name.isEmpty()) {
                name = "MHF_Steve";
            }

            source = "https://minotar.net/skin/" + urlEncode(name);
        }

        Image image = new Image(source, false);

        if (image.isError()) {
            throw new Exception("No se pudo cargar la skin.");
        }

        return image;
    }

    private static Image loadCape(File capeFile) {
        if (capeFile == null || !capeFile.exists()) {
            return null;
        }

        Image image = new Image(capeFile.toURI().toString(), false);

        if (image.isError()) {
            return null;
        }

        return image;
    }

    private static Group createPlayerModel(Image skin, Image cape) {
        Group root = new Group();

        boolean modernSkin = skin.getHeight() >= skin.getWidth();

        Image preparedSkin = prepareSkinTexture(skin);

        PhongMaterial skinMaterial = new PhongMaterial();
        skinMaterial.setDiffuseMap(preparedSkin);
        skinMaterial.setSpecularColor(Color.TRANSPARENT);

    /*
     Modelo:
     - Eje X: izquierda/derecha
     - Eje Y: arriba/abajo
     - Eje Z: profundidad
    */

        // Head.
        root.getChildren().add(createCuboid(
                skinMaterial,
                0, -12, 0,
                8, 8, 8,
                new CuboidUV(
                        r(8, 8, 8, 8),
                        r(24, 8, 8, 8),
                        r(16, 8, 8, 8),
                        r(0, 8, 8, 8),
                        r(8, 0, 8, 8),
                        r(16, 0, 8, 8)
                ),
                preparedSkin
        ));

        // Body.
        root.getChildren().add(createCuboid(
                skinMaterial,
                0, -2, 0,
                8, 12, 4,
                new CuboidUV(
                        r(20, 20, 8, 12),
                        r(32, 20, 8, 12),
                        r(28, 20, 4, 12),
                        r(16, 20, 4, 12),
                        r(20, 16, 8, 4),
                        r(28, 16, 8, 4)
                ),
                preparedSkin
        ));

        // Right arm.
        root.getChildren().add(createCuboid(
                skinMaterial,
                -6, -2, 0,
                4, 12, 4,
                new CuboidUV(
                        r(44, 20, 4, 12),
                        r(52, 20, 4, 12),
                        r(48, 20, 4, 12),
                        r(40, 20, 4, 12),
                        r(44, 16, 4, 4),
                        r(48, 16, 4, 4)
                ),
                preparedSkin
        ));

        // Left arm.
        if (modernSkin) {
            root.getChildren().add(createCuboid(
                    skinMaterial,
                    6, -2, 0,
                    4, 12, 4,
                    new CuboidUV(
                            r(36, 52, 4, 12),
                            r(44, 52, 4, 12),
                            r(40, 52, 4, 12),
                            r(32, 52, 4, 12),
                            r(36, 48, 4, 4),
                            r(40, 48, 4, 4)
                    ),
                    preparedSkin
            ));
        } else {
            root.getChildren().add(createCuboid(
                    skinMaterial,
                    6, -2, 0,
                    4, 12, 4,
                    new CuboidUV(
                            r(44, 20, 4, 12),
                            r(52, 20, 4, 12),
                            r(48, 20, 4, 12),
                            r(40, 20, 4, 12),
                            r(44, 16, 4, 4),
                            r(48, 16, 4, 4)
                    ),
                    preparedSkin
            ));
        }

        // Right leg.
        root.getChildren().add(createCuboid(
                skinMaterial,
                -2, 10, 0,
                4, 12, 4,
                new CuboidUV(
                        r(4, 20, 4, 12),
                        r(12, 20, 4, 12),
                        r(8, 20, 4, 12),
                        r(0, 20, 4, 12),
                        r(4, 16, 4, 4),
                        r(8, 16, 4, 4)
                ),
                preparedSkin
        ));

        // Left leg.
        if (modernSkin) {
            root.getChildren().add(createCuboid(
                    skinMaterial,
                    2, 10, 0,
                    4, 12, 4,
                    new CuboidUV(
                            r(20, 52, 4, 12),
                            r(28, 52, 4, 12),
                            r(24, 52, 4, 12),
                            r(16, 52, 4, 12),
                            r(20, 48, 4, 4),
                            r(24, 48, 4, 4)
                    ),
                    preparedSkin
            ));
        } else {
            root.getChildren().add(createCuboid(
                    skinMaterial,
                    2, 10, 0,
                    4, 12, 4,
                    new CuboidUV(
                            r(4, 20, 4, 12),
                            r(12, 20, 4, 12),
                            r(8, 20, 4, 12),
                            r(0, 20, 4, 12),
                            r(4, 16, 4, 4),
                            r(8, 16, 4, 4)
                    ),
                    preparedSkin
            ));
        }

        if (modernSkin) {
            addModernOverlays(root, skinMaterial, preparedSkin);
        }

        if (cape != null) {
            root.getChildren().add(createCape(prepareCapeTexture(cape)));
        }

        root.setTranslateY(2);

        return root;
    }

    private static void addModernOverlays(Group root, PhongMaterial skinMaterial, Image skin) {
        // Hat layer.
        root.getChildren().add(createCuboid(
                skinMaterial,
                0, -12, 0,
                8.65, 8.65, 8.65,
                new CuboidUV(
                        r(40, 8, 8, 8),
                        r(56, 8, 8, 8),
                        r(48, 8, 8, 8),
                        r(32, 8, 8, 8),
                        r(40, 0, 8, 8),
                        r(48, 0, 8, 8)
                ),
                skin
        ));

        // Body jacket.
        root.getChildren().add(createCuboid(
                skinMaterial,
                0, -2, 0,
                8.55, 12.55, 4.55,
                new CuboidUV(
                        r(20, 36, 8, 12),
                        r(32, 36, 8, 12),
                        r(28, 36, 4, 12),
                        r(16, 36, 4, 12),
                        r(20, 32, 8, 4),
                        r(28, 32, 8, 4)
                ),
                skin
        ));

        // Right sleeve.
        root.getChildren().add(createCuboid(
                skinMaterial,
                -6, -2, 0,
                4.45, 12.45, 4.45,
                new CuboidUV(
                        r(44, 36, 4, 12),
                        r(52, 36, 4, 12),
                        r(48, 36, 4, 12),
                        r(40, 36, 4, 12),
                        r(44, 32, 4, 4),
                        r(48, 32, 4, 4)
                ),
                skin
        ));

        // Left sleeve.
        root.getChildren().add(createCuboid(
                skinMaterial,
                6, -2, 0,
                4.45, 12.45, 4.45,
                new CuboidUV(
                        r(52, 52, 4, 12),
                        r(60, 52, 4, 12),
                        r(56, 52, 4, 12),
                        r(48, 52, 4, 12),
                        r(52, 48, 4, 4),
                        r(56, 48, 4, 4)
                ),
                skin
        ));

        // Right pants.
        root.getChildren().add(createCuboid(
                skinMaterial,
                -2, 10, 0,
                4.45, 12.45, 4.45,
                new CuboidUV(
                        r(4, 36, 4, 12),
                        r(12, 36, 4, 12),
                        r(8, 36, 4, 12),
                        r(0, 36, 4, 12),
                        r(4, 32, 4, 4),
                        r(8, 32, 4, 4)
                ),
                skin
        ));

        // Left pants.
        root.getChildren().add(createCuboid(
                skinMaterial,
                2, 10, 0,
                4.45, 12.45, 4.45,
                new CuboidUV(
                        r(4, 52, 4, 12),
                        r(12, 52, 4, 12),
                        r(8, 52, 4, 12),
                        r(0, 52, 4, 12),
                        r(4, 48, 4, 4),
                        r(8, 48, 4, 4)
                ),
                skin
        ));
    }

    private static MeshView createCuboid(
            PhongMaterial material,
            double cx,
            double cy,
            double cz,
            double w,
            double h,
            double d,
            CuboidUV uv,
            Image image
    ) {
        TriangleMesh mesh = new TriangleMesh();

        double x1 = cx - w / 2.0;
        double x2 = cx + w / 2.0;
        double y1 = cy - h / 2.0;
        double y2 = cy + h / 2.0;
        double z1 = cz - d / 2.0;
        double z2 = cz + d / 2.0;

        // Front faces camera initially.
        addQuad(mesh, image, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, uv.front);

        // Back.
        addQuad(mesh, image, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, uv.back);

        // Left.
        addQuad(mesh, image, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2, uv.left);

        // Right.
        addQuad(mesh, image, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, uv.right);

        // Top.
        addQuad(mesh, image, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, uv.top);

        // Bottom.
        addQuad(mesh, image, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, uv.bottom);

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setMouseTransparent(true);

        return view;
    }

    private static void addQuad(
            TriangleMesh mesh,
            Image image,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            Rect rect
    ) {
        int pointIndex = mesh.getPoints().size() / 3;
        int texIndex = mesh.getTexCoords().size() / 2;

        mesh.getPoints().addAll(
                (float) x0, (float) y0, (float) z0,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                (float) x3, (float) y3, (float) z3
        );

    /*
     Importante:
     Aunque la textura esté ampliada internamente a 1024x1024 para verse nítida,
     las coordenadas UV siguen usando el layout lógico Minecraft 64x64.
    */
        double iw = 64.0;
        double ih = 64.0;

        float u0 = (float) (rect.x / iw);
        float v0 = (float) (rect.y / ih);
        float u1 = (float) ((rect.x + rect.w) / iw);
        float v1 = (float) ((rect.y + rect.h) / ih);

        mesh.getTexCoords().addAll(
                u0, v0,
                u1, v0,
                u1, v1,
                u0, v1
        );

        mesh.getFaces().addAll(
                pointIndex, texIndex,
                pointIndex + 1, texIndex + 1,
                pointIndex + 2, texIndex + 2,

                pointIndex, texIndex,
                pointIndex + 2, texIndex + 2,
                pointIndex + 3, texIndex + 3
        );
    }

    private static MeshView createCape(Image cape) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(cape);
        material.setSpecularColor(Color.TRANSPARENT);

        TriangleMesh mesh = new TriangleMesh();

        double w = 10;
        double h = 16;

        double x1 = -w / 2.0;
        double x2 = w / 2.0;
        double y1 = -8;
        double y2 = y1 + h;
        double z = 3.15;

        mesh.getPoints().addAll(
                (float) x1, (float) y1, (float) z,
                (float) x2, (float) y1, (float) z,
                (float) x2, (float) y2, (float) z,
                (float) x1, (float) y2, (float) z
        );

        mesh.getTexCoords().addAll(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f
        );

        mesh.getFaces().addAll(
                0, 0,
                1, 1,
                2, 2,

                0, 0,
                2, 2,
                3, 3
        );

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setTranslateY(1.2);
        view.setMouseTransparent(true);

        Rotate tilt = new Rotate(8, Rotate.X_AXIS);
        view.getTransforms().add(tilt);

        return view;
    }

    private static void setupMouseControls(
            SubScene subScene,
            Rotate rotateX,
            Rotate rotateY,
            PerspectiveCamera camera
    ) {
        final double[] mouseOld = new double[2];
        final double[] angleOld = new double[2];

        subScene.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            mouseOld[0] = event.getSceneX();
            mouseOld[1] = event.getSceneY();
            angleOld[0] = rotateX.getAngle();
            angleOld[1] = rotateY.getAngle();
            event.consume();
        });

        subScene.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            double dx = event.getSceneX() - mouseOld[0];
            double dy = event.getSceneY() - mouseOld[1];

            rotateY.setAngle(angleOld[1] + dx * 0.45);

            double nextX = angleOld[0] - dy * 0.35;

            if (nextX < -55) {
                nextX = -55;
            }

            if (nextX > 45) {
                nextX = 45;
            }

            rotateX.setAngle(nextX);
            event.consume();
        });

    /*
     Antes el zoom se hacía con cualquier movimiento de rueda/trackpad.
     En algunos portátiles eso produce zoom continuo.
     Ahora solo hace zoom si mantienes CTRL pulsado.
    */
        subScene.addEventHandler(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown()) {
                event.consume();
                return;
            }

            double delta = event.getDeltaY();

            if (Math.abs(delta) < 2) {
                event.consume();
                return;
            }

            double nextZ = camera.getTranslateZ() + delta * 0.045;

            if (nextZ > -55) {
                nextZ = -55;
            }

            if (nextZ < -130) {
                nextZ = -130;
            }

            camera.setTranslateZ(nextZ);
            event.consume();
        });
    }

    private static Image prepareSkinTexture(Image source) {
    /*
     Minecraft usa skins 64x64 o 64x32.
     JavaFX suaviza mucho las texturas pequeñas.
     Solución: normalizamos a 64x64 y luego ampliamos en modo pixel-perfect.
    */
        WritableImage normalized = normalizeSkinTo64(source);
        return upscaleNearest(normalized, 16);
    }

    private static Image prepareCapeTexture(Image source) {
        return upscaleNearest(source, 8);
    }

    private static WritableImage normalizeSkinTo64(Image source) {
        WritableImage target = new WritableImage(64, 64);

        PixelReader reader = source.getPixelReader();
        PixelWriter writer = target.getPixelWriter();

        if (reader == null) {
            return target;
        }

        int sw = Math.max(1, (int) Math.round(source.getWidth()));
        int sh = Math.max(1, (int) Math.round(source.getHeight()));

    /*
     Si es skin antigua 64x32, solo copiamos la mitad superior.
     Si es moderna 64x64, copiamos todo.
    */
        int logicalSourceHeight = sh >= sw ? 64 : 32;

        for (int y = 0; y < logicalSourceHeight; y++) {
            for (int x = 0; x < 64; x++) {
                int sx = clamp((int) Math.floor((x / 64.0) * sw), 0, sw - 1);
                int sy = clamp((int) Math.floor((y / (double) logicalSourceHeight) * sh), 0, sh - 1);

                writer.setArgb(x, y, reader.getArgb(sx, sy));
            }
        }

        return target;
    }

    private static Image upscaleNearest(Image source, int scale) {
        PixelReader reader = source.getPixelReader();

        if (reader == null) {
            return source;
        }

        int sw = Math.max(1, (int) Math.round(source.getWidth()));
        int sh = Math.max(1, (int) Math.round(source.getHeight()));

        int tw = sw * scale;
        int th = sh * scale;

        WritableImage target = new WritableImage(tw, th);
        PixelWriter writer = target.getPixelWriter();

        for (int y = 0; y < th; y++) {
            int sy = y / scale;

            for (int x = 0; x < tw; x++) {
                int sx = x / scale;
                writer.setArgb(x, y, reader.getArgb(sx, sy));
            }
        }

        return target;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }

    private static Rect r(double x, double y, double w, double h) {
        return new Rect(x, y, w, h);
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
}