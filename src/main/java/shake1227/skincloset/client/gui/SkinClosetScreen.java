package shake1227.skincloset.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import shake1227.skincloset.Constants;
import shake1227.skincloset.network.C2SChangeSkinPacket;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.SkinCache;
import shake1227.skincloset.skin.SkinDownloader;
import shake1227.skincloset.skin.SkinProfile;

import java.nio.file.Files; // エラー修正のためインポートを追加
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SkinClosetScreen extends Screen {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/background.png");
    private static final Component TITLE = Component.translatable("gui.skincloset.title");
    private static final Component EDITING_TITLE = Component.translatable("gui.skincloset.editing");
    private static final Component ADD_BY_NAME_TITLE = Component.translatable("gui.skincloset.add_by_name");
    private static final Component ADD_FROM_LOCAL_TITLE = Component.translatable("gui.skincloset.add_from_local");

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
    private int skinsPerPage = 3;
    private int totalPages = 0;

    // --- EDITING State ---
    private SkinProfile selectedProfile;
    private PlayerPreviewWidget editingPreview;
    private EditBox nameEditBox;

    // --- ADD State ---
    private EditBox inputEditBox;
    private Component statusMessage = Component.empty();

    public SkinClosetScreen() {
        super(TITLE);
        this.skinProfiles = SkinCache.getProfiles();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.skinProfiles = SkinCache.getProfiles();
        this.totalPages = (int) Math.ceil((double) this.skinProfiles.size() / this.skinsPerPage);

        // 現在の状態に応じてGUIを構築
        switch (this.currentState) {
            case LIST:
                buildListWidgets();
                break;
            case EDITING:
                buildEditingWidgets();
                break;
            case ADD_BY_NAME:
                buildAddByNameWidgets();
                break;
            case ADD_FROM_LOCAL:
                buildAddFromLocalWidgets();
                break;
        }
    }

    // --- Widget Builders ---

    private void buildListWidgets() {
        int centerX = this.width / 2;
        int topY = this.height / 2 - 80;
        int listWidth = skinsPerPage * 110; // プレビュー幅100 + マージン10
        int startX = centerX - (listWidth / 2) + 5; // リストの開始X座標

        // 1. プレビューの表示
        int startIndex = currentPage * skinsPerPage;
        for (int i = 0; i < skinsPerPage; i++) {
            int profileIndex = startIndex + i;
            if (profileIndex >= this.skinProfiles.size()) break; // リストの終端

            SkinProfile profile = this.skinProfiles.get(profileIndex);
            int xPos = startX + (i * 110);
            int yPos = topY + 20;

            // プレビューウィジェット
            PlayerPreviewWidget preview = new PlayerPreviewWidget(xPos, yPos, 100, 150, profile.getGameProfile());
            this.addRenderableWidget(preview);

            // 編集ボタン
            this.addRenderableWidget(Button.builder(Component.literal(profile.getName()), (button) -> {
                this.selectedProfile = profile;
                this.currentState = State.EDITING;
                this.init(); // GUI再構築
            }).bounds(xPos + 5, yPos + 155, 90, 20).build());
        }

        // 2. ページネーションボタン
        if (currentPage > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("< Prev"), (button) -> {
                this.currentPage--;
                this.init();
            }).bounds(startX - 50, topY + 80, 40, 20).build());
        }
        if (currentPage < totalPages - 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Next >"), (button) -> {
                this.currentPage++;
                this.init();
            }).bounds(startX + listWidth + 10, topY + 80, 40, 20).build());
        }

        // 3. 追加ボタン
        int buttonY = this.height / 2 + 100;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_by_name"), (button) -> {
            this.currentState = State.ADD_BY_NAME;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(centerX - 155, buttonY, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_from_local"), (button) -> {
            this.currentState = State.ADD_FROM_LOCAL;
            this.statusMessage = Component.translatable("gui.skincloset.local.info");
            this.init();
        }).bounds(centerX + 5, buttonY, 150, 20).build());
    }

    private void buildEditingWidgets() {
        if (this.selectedProfile == null) {
            this.currentState = State.LIST;
            this.init();
            return;
        }

        int centerX = this.width / 2;
        int topY = this.height / 2 - 80;

        // プレビュー
        this.editingPreview = new PlayerPreviewWidget(centerX - 75, topY, 150, 250, selectedProfile.getGameProfile());
        this.addRenderableWidget(this.editingPreview);

        // 名前変更
        this.nameEditBox = new EditBox(this.font, centerX + 90, topY + 20, 100, 20, Component.translatable("gui.skincloset.profile_name"));
        this.nameEditBox.setValue(selectedProfile.getName());
        this.addRenderableWidget(this.nameEditBox);

        // ボタン
        int buttonX = centerX - 75;
        int buttonY = topY + 250 + 10;

        // 適用ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.apply"), (button) -> {
            Optional<SkinProfile.SkinData> data = selectedProfile.getSkinData();
            if (data.isPresent()) {
                PacketRegistry.CHANNEL.sendToServer(new C2SChangeSkinPacket(data.get().value(), data.get().signature()));
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.applied", selectedProfile.getName()));
                this.onClose(); // 適用したら閉じる
            }
        }).bounds(buttonX, buttonY, 150, 20).build());

        // 名前保存ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.save_name"), (button) -> {
            String newName = this.nameEditBox.getValue();
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(selectedProfile.getName())) {
                selectedProfile.setName(newName);
                SkinCache.saveProfiles();
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.name_saved", newName));
            }
        }).bounds(centerX + 90, topY + 45, 100, 20).build());


        // 削除ボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.delete"), (button) -> {
            SkinCache.removeProfile(selectedProfile);
            SkinCache.saveProfiles();
            this.selectedProfile = null;
            this.currentState = State.LIST;
            this.init();
        }).bounds(centerX + 90, topY + 75, 100, 20).build());

        // ダウンロードボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.download"), (button) -> {
            downloadSkin();
        }).bounds(centerX + 90, topY + 100, 100, 20).build());


        // 戻るボタン
        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.selectedProfile = null;
            this.currentState = State.LIST;
            this.init();
        }).bounds(10, 10, 60, 20).build());
    }

    private void buildAddByNameWidgets() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.inputEditBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.translatable("gui.skincloset.username"));
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.fetch"), (button) -> {
            fetchSkinByName(this.inputEditBox.getValue());
        }).bounds(centerX - 100, centerY + 20, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(10, 10, 60, 20).build());
    }

    private void buildAddFromLocalWidgets() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.inputEditBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.translatable("gui.skincloset.profile_name"));
        this.inputEditBox.setHint(Component.translatable("gui.skincloset.local.hint")); // "example.png"
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.upload"), (button) -> {
            uploadSkinFromLocal(this.inputEditBox.getValue());
        }).bounds(centerX - 100, centerY + 20, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(10, 10, 60, 20).build());
    }


    // --- Logic ---

    private void fetchSkinByName(String username) {
        if (username == null || username.trim().isEmpty()) {
            this.statusMessage = Component.translatable("gui.skincloset.error.no_name");
            return;
        }
        this.statusMessage = Component.translatable("gui.skincloset.fetching");

        SkinDownloader.fetchSkinByUsername(username, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.statusMessage = Component.translatable("gui.skincloset.success.added", profile.getName());
                this.currentState = State.LIST;
                this.init();
            } else {
                this.statusMessage = Component.translatable("gui.skincloset.error.not_found", username);
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

        if (!Files.exists(localSkinPath)) { // Files をインポート
            this.statusMessage = Component.translatable("gui.skincloset.error.local_not_found", fileName);
            return;
        }

        this.statusMessage = Component.translatable("gui.skincloset.uploading");

        SkinDownloader.uploadSkinFromLocal(profileName, localSkinPath, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.statusMessage = Component.translatable("gui.skincloset.success.uploaded", profile.getName());
                this.currentState = State.LIST;
                this.init();
            } else {
                this.statusMessage = Component.translatable("gui.skincloset.error.upload_failed");
            }
        });
    }

    private void downloadSkin() {
        if (this.selectedProfile == null) return;

        Optional<String> urlOpt = this.selectedProfile.getTextureUrl();
        if(urlOpt.isEmpty()) {
            this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.error.no_texture"));
            return;
        }

        Path downloadsDir = Path.of(System.getProperty("user.home"), "Downloads");
        String fileName = this.selectedProfile.getName() + ".png";
        Path targetPath = downloadsDir.resolve(fileName);

        SkinDownloader.downloadSkinTexture(urlOpt.get(), targetPath, (success) -> {
            if(success) {
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.success.downloaded", fileName));
            } else {
                this.minecraft.player.sendSystemMessage(Component.translatable("gui.skincloset.error.download_failed"));
            }
        });
    }

    // --- Render ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景を半透明の黒にする
        this.renderBackground(graphics); // 1.20.1では引数がGuiGraphicsのみ

        // タイトル
        Component currentTitle = switch (this.currentState) {
            case LIST -> TITLE;
            case EDITING -> Component.translatable("gui.skincloset.editing", this.selectedProfile.getName());
            case ADD_BY_NAME -> ADD_BY_NAME_TITLE;
            case ADD_FROM_LOCAL -> ADD_FROM_LOCAL_TITLE;
        };
        graphics.drawCenteredString(this.font, currentTitle, this.width / 2, 15, 0xFFFFFF);

        // ステータスメッセージ
        if (this.currentState == State.ADD_BY_NAME || this.currentState == State.ADD_FROM_LOCAL) {
            graphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height / 2 + 50, 0xFFFF55);
        }

        // ウィジェット（ボタン、プレビューなど）を描画
        super.render(graphics, mouseX, mouseY, partialTick);

        // ページ番号
        if (this.currentState == State.LIST && this.totalPages > 0) {
            String pageText = String.format("Page %d / %d", this.currentPage + 1, this.totalPages);
            graphics.drawCenteredString(this.font, pageText, this.width / 2, this.height / 2 + 125, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

