package shake1227.skincloset.client.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import shake1227.skincloset.network.C2SChangeSkinPacket;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.SkinCache;
import shake1227.skincloset.skin.SkinDownloader;
import shake1227.skincloset.skin.SkinProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SkinClosetScreen extends Screen {

    // --- モダンUIレイアウト定数 ---
    private static final int SIDEBAR_WIDTH = 100;
    private static final int CONTENT_PADDING = 10;
    private static final int SKIN_GRID_WIDTH = 100;
    private static final int SKIN_GRID_HEIGHT = 150;
    private static final int SKIN_GRID_COLS = 3;
    private static final int SKIN_GRID_ROWS = 2;
    // ----------------------------

    private enum State {
        LIST,
        EDITING,
        ADD_BY_NAME,
        ADD_FROM_LOCAL
    }

    private State currentState = State.LIST;
    private List<SkinProfile> skinProfiles;

    // --- LIST State ---
    private int currentPage = 0;
    private int skinsPerPage = SKIN_GRID_COLS * SKIN_GRID_ROWS; // 6
    private int totalPages = 0;

    // --- EDITING State ---
    private SkinProfile selectedProfile;
    private EditBox nameEditBox;

    // --- ADD State ---
    private EditBox inputEditBox;
    private Component statusMessage = Component.empty();

    public SkinClosetScreen() {
        super(Component.translatable("gui.skincloset.title"));
        this.skinProfiles = SkinCache.getProfiles();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.skinProfiles = SkinCache.getProfiles();
        this.totalPages = (int) Math.ceil((double) this.skinProfiles.size() / this.skinsPerPage);

        // 1. サイドバーを常に描画
        buildSidebarWidgets();

        // 2. メインコンテンツを状態に応じて描画
        switch (this.currentState) {
            case LIST:
                this.title = Component.translatable("gui.skincloset.title");
                buildListWidgets();
                break;
            case EDITING:
                if (this.selectedProfile == null) { // 安全対策
                    this.currentState = State.LIST;
                    this.init();
                    return;
                }
                this.title = Component.translatable("gui.skincloset.editing", this.selectedProfile.getName());
                buildEditingWidgets();
                break;
            case ADD_BY_NAME:
                this.title = Component.translatable("gui.skincloset.add_by_name");
                buildAddByNameWidgets();
                break;
            case ADD_FROM_LOCAL:
                this.title = Component.translatable("gui.skincloset.add_from_local");
                buildAddFromLocalWidgets();
                break;
        }
    }

    // --- Widget Builders ---

    private void buildSidebarWidgets() {
        int xPos = CONTENT_PADDING;
        int yPos = 30; // タイトルの下

        // 「自分のスキンを追加」ボタン (新機能)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_my_skin"), (button) -> {
            addPlayerCurrentSkin();
        }).bounds(xPos, yPos, SIDEBAR_WIDTH - (CONTENT_PADDING * 2), 20).build());

        yPos += 25;

        // 「名前で追加」ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_by_name"), (button) -> {
            this.currentState = State.ADD_BY_NAME;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(xPos, yPos, SIDEBAR_WIDTH - (CONTENT_PADDING * 2), 20).build());

        yPos += 25;

        // 「ローカルから追加」ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_from_local"), (button) -> {
            this.currentState = State.ADD_FROM_LOCAL;
            this.statusMessage = Component.translatable("gui.skincloset.local.info");
            this.init();
        }).bounds(xPos, yPos, SIDEBAR_WIDTH - (CONTENT_PADDING * 2), 20).build());
    }

    private void buildListWidgets() {
        int contentX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentY = 30; // タイトルの下
        int skinWithPadding = SKIN_GRID_WIDTH + CONTENT_PADDING;
        int skinHeightWithPadding = SKIN_GRID_HEIGHT + 25 + 25; // スキン + 編集ボタン + 適用ボタン

        // 1. グリッドでスキンを表示
        int startIndex = currentPage * skinsPerPage;
        for (int i = 0; i < skinsPerPage; i++) {
            int profileIndex = startIndex + i;
            if (profileIndex >= this.skinProfiles.size()) break; // リストの終端

            SkinProfile profile = this.skinProfiles.get(profileIndex);

            int col = i % SKIN_GRID_COLS;
            int row = i / SKIN_GRID_COLS;

            int xPos = contentX + (col * skinWithPadding);
            int yPos = contentY + (row * skinHeightWithPadding);

            // プレビューウィジェット
            PlayerPreviewWidget preview = new PlayerPreviewWidget(xPos, yPos, SKIN_GRID_WIDTH, SKIN_GRID_HEIGHT, profile.getGameProfile());
            this.addRenderableWidget(preview);

            // 編集ボタン
            this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.edit"), (button) -> {
                this.selectedProfile = profile;
                this.currentState = State.EDITING;
                this.init(); // GUI再構築
            }).bounds(xPos, yPos + SKIN_GRID_HEIGHT + 5, SKIN_GRID_WIDTH, 20).build());

            // 適用ボタン (新レイアウト)
            this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.apply"), (button) -> {
                applySkin(profile);
            }).bounds(xPos, yPos + SKIN_GRID_HEIGHT + 30, SKIN_GRID_WIDTH, 20).build());
        }

        // 2. ページネーションボタン
        int paginationY = this.height - 30;
        int paginationX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2;

        if (currentPage > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("< Prev"), (button) -> {
                this.currentPage--;
                this.init();
            }).bounds(paginationX - 85, paginationY, 80, 20).build());
        }
        if (currentPage < totalPages - 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Next >"), (button) -> {
                this.currentPage++;
                this.init();
            }).bounds(paginationX + 5, paginationY, 80, 20).build());
        }
    }

    private void buildEditingWidgets() {
        int contentX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentY = 30; // タイトルの下
        int previewSize = 250;

        // 左側にプレビュー
        PlayerPreviewWidget preview = new PlayerPreviewWidget(contentX, contentY, previewSize, previewSize, selectedProfile.getGameProfile());
        this.addRenderableWidget(preview);

        // 右側にオプション
        int optionsX = contentX + previewSize + CONTENT_PADDING;
        int optionsY = contentY;
        int optionsWidth = 150;

        // 名前変更
        this.nameEditBox = new EditBox(this.font, optionsX, optionsY + 20, optionsWidth, 20, Component.translatable("gui.skincloset.profile_name"));
        this.nameEditBox.setValue(selectedProfile.getName());
        this.addRenderableWidget(this.nameEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.save_name"), (button) -> {
            saveName();
        }).bounds(optionsX, optionsY + 45, optionsWidth, 20).build());

        optionsY += 75; // スペース

        // 適用ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.apply"), (button) -> {
            applySkin(selectedProfile);
        }).bounds(optionsX, optionsY, optionsWidth, 20).build());

        optionsY += 25;

        // ダウンロードボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.download"), (button) -> {
            downloadSkin();
        }).bounds(optionsX, optionsY, optionsWidth, 20).build());

        optionsY += 25;

        // 削除ボタン (赤文字)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.delete"), (button) -> {
            SkinCache.removeProfile(selectedProfile);
            SkinCache.saveProfiles();
            this.selectedProfile = null;
            this.currentState = State.LIST;
            this.init();
        }).build()).updateMessage(Component.translatable("gui.skincloset.delete").withStyle(s -> s.withColor(0xFF5555)));
        this.getLastAddedWidget().setPosition(optionsX, optionsY);
        this.getLastAddedWidget().setWidth(optionsWidth);


        // 戻るボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.selectedProfile = null;
            this.currentState = State.LIST;
            this.init();
        }).bounds(optionsX, this.height - 40, optionsWidth, 20).build());
    }

    private void buildAddByNameWidgets() {
        int contentX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2 - 100;
        int contentY = this.height / 2 - 20;

        this.inputEditBox = new EditBox(this.font, contentX, contentY, 200, 20, Component.translatable("gui.skincloset.username"));
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.fetch"), (button) -> {
            fetchSkinByName(this.inputEditBox.getValue());
        }).bounds(contentX, contentY + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(contentX, contentY + 50, 200, 20).build());
    }

    private void buildAddFromLocalWidgets() {
        int contentX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2 - 100;
        int contentY = this.height / 2 - 20;

        this.inputEditBox = new EditBox(this.font, contentX, contentY, 200, 20, Component.translatable("gui.skincloset.profile_name"));
        this.inputEditBox.setHint(Component.translatable("gui.skincloset.local.hint")); // "example.png"
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.upload"), (button) -> {
            uploadSkinFromLocal(this.inputEditBox.getValue());
        }).bounds(contentX, contentY + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(contentX, contentY + 50, 200, 20).build());
    }


    // --- Logic ---

    private void applySkin(SkinProfile profile) {
        if (profile == null) return;
        Optional<SkinProfile.SkinData> data = profile.getSkinData();
        if (data.isPresent()) {
            PacketRegistry.CHANNEL.sendToServer(new C2SChangeSkinPacket(data.get().value(), data.get().signature()));
            this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.applied", profile.getName()));
            this.onClose(); // 適用したら閉じる
        }
    }

    private void saveName() {
        if (this.selectedProfile == null || this.nameEditBox == null) return;

        String newName = this.nameEditBox.getValue();
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(selectedProfile.getName())) {
            selectedProfile.setName(newName);
            SkinCache.saveProfiles();
            this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.name_saved", newName));
            this.title = Component.translatable("gui.skincloset.editing", this.selectedProfile.getName());
        }
    }

    private void addPlayerCurrentSkin() {
        try {
            GameProfile profile = this.minecraft.getUser().getProfile();
            Optional<Property> textures = profile.getProperties().get("textures").stream().findFirst();

            textures.ifPresentOrElse(
                    (texture) -> {
                        SkinProfile newProfile = new SkinProfile(profile.getName(), profile.getId(), texture.getValue(), texture.getSignature());
                        SkinCache.addProfile(newProfile);
                        SkinCache.saveProfiles();
                        this.statusMessage = Component.translatable("gui.skincloset.success.added_self");
                        this.currentState = State.LIST;
                        this.init();
                    },
                    () -> {
                        this.statusMessage = Component.translatable("gui.skincloset.error.self_no_skin");
                    }
            );
        } catch (Exception e) {
            this.statusMessage = Component.translatable("gui.skincloset.error.self_no_skin");
        }
    }

    private void fetchSkinByName(String username) {
        if (username == null || username.trim().isEmpty()) {
            this.statusMessage = Component.translatable("gui.skincloset.error.no_name");
            return;
        }
        this.statusMessage = Component.translatable("gui.skincloset.fetching");
        this.inputEditBox.setEditable(false); // 処理中は編集不可

        SkinDownloader.fetchSkinByUsername(username, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.statusMessage = Component.translatable("gui.skincloset.success.added", profile.getName());
                this.currentState = State.LIST;
                // GUIの更新はメインスレッドで行う
                this.minecraft.execute(this::init);
            } else {
                this.statusMessage = Component.translatable("gui.skincloset.error.not_found", username);
                this.inputEditBox.setEditable(true);
            }
        });
    }

    private void uploadSkinFromLocal(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            this.statusMessage = Component.translatable("gui.skincloset.error.no_profile_name");
            return;
        }

        String fileName = profileName.endsWith(".png") ? profileName : profileName + ".png";
        Path localSkinPath = SkinCache.UPLOADS_DIR.resolve(fileName);

        if (!Files.exists(localSkinPath)) {
            this.statusMessage = Component.translatable("gui.skincloset.error.local_not_found", fileName);
            return;
        }

        this.statusMessage = Component.translatable("gui.skincloset.uploading");
        this.inputEditBox.setEditable(false); // 処理中は編集不可

        SkinDownloader.uploadSkinFromLocal(profileName, localSkinPath, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.statusMessage = Component.translatable("gui.skincloset.success.uploaded", profile.getName());
                this.currentState = State.LIST;
                // GUIの更新はメインスレッドで行う
                this.minecraft.execute(this::init);
            } else {
                this.statusMessage = Component.translatable("gui.skincloset.error.upload_failed");
                this.inputEditBox.setEditable(true);
            }
        });
    }

    private void downloadSkin() {
        if (this.selectedProfile == null) return;

        Optional<String> urlOpt = this.selectedProfile.getTextureUrl();
        if (urlOpt.isEmpty()) {
            this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.error.no_texture"));
            return;
        }

        Path downloadsDir = Path.of(System.getProperty("user.home"), "Downloads");
        String fileName = this.selectedProfile.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
        Path targetPath = downloadsDir.resolve(fileName);

        SkinDownloader.downloadSkinTexture(urlOpt.get(), targetPath, (success) -> {
            if (success) {
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.success.downloaded", fileName));
            } else {
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.error.download_failed"));
            }
        });
    }

    // --- Render ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // --- 修正: ぼかし背景をレンダリング ---
        this.renderBackground(graphics);

        // サイドバーの背景を描画
        graphics.fill(0, 0, SIDEBAR_WIDTH, this.height, 0x80000000); // 半透明の黒

        // ウィジェット（ボタン、プレビューなど）を描画
        super.render(graphics, mouseX, mouseY, partialTick);

        // タイトル
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // ステータスメッセージ
        if (this.currentState == State.ADD_BY_NAME || this.currentState == State.ADD_FROM_LOCAL) {
            int contentX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2;
            graphics.drawCenteredString(this.font, this.statusMessage, contentX, this.height / 2 + 50, 0xFFFF55);
        }

        // ページ番号
        if (this.currentState == State.LIST && this.totalPages > 0) {
            String pageText = Component.translatable("gui.skincloset.page", this.currentPage + 1, this.totalPages).getString();
            int paginationX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2;
            graphics.drawCenteredString(this.font, pageText, paginationX - 35, this.height - 25, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        // falseを返すことで、シングルプレイ時にゲームが停止しないようにする
        return false;
    }
}