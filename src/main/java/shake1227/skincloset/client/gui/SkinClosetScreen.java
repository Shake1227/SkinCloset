package shake1227.skincloset.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import shake1227.skincloset.SkinCloset;
import shake1227.skincloset.network.C2SChangeSkinPacket;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.LastSkinCache;
import shake1227.skincloset.skin.SkinCache;
import shake1227.skincloset.skin.SkinDownloader;
import shake1227.skincloset.skin.SkinProfile;
import net.minecraft.ChatFormatting;
import shake1227.skincloset.compat.ModernNotificationCompat;
import shake1227.modernnotification.core.NotificationCategory;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SkinClosetScreen extends Screen {

    private static final float REFERENCE_HEIGHT = 480.0f;

    private static final int V_SIDEBAR_WIDTH = 109;
    private static final int V_PADDING = 9;
    private static final int V_BUTTON_HEIGHT = 25;
    private static final int V_TOP_MARGIN = 36;

    private static final int V_PREVIEW_WIDTH = 182;
    private static final int V_PREVIEW_HEIGHT = 273;
    private static final int V_PREVIEW_TO_EDIT_GAP = 14;
    private static final int V_EDIT_TO_APPLY_GAP = 9;

    private static final int SKIN_GRID_COLS = 4;
    private static final int SKIN_GRID_ROWS = 1;

    private static final int V_EDIT_PREVIEW_WIDTH = 246;
    private static final int V_EDIT_PREVIEW_HEIGHT = 324;
    private static final int V_EDIT_OPTIONS_WIDTH = 127;

    private static final float TITLE_TEXT_SCALE = 2.2f;
    private static final float GENERAL_TEXT_SCALE = 1.5f;

    private static final int V_NAME_TAG_HEIGHT = 5;


    private enum State {
        LIST,
        EDITING,
        ADD_BY_NAME,
        ADD_FROM_LOCAL
    }

    private State currentState = State.LIST;
    private List<SkinProfile> skinProfiles;

    private int currentPage = 0;
    private int skinsPerPage = SKIN_GRID_COLS * SKIN_GRID_ROWS;
    private int totalPages = 0;

    private SkinProfile selectedProfile;
    private EditBox nameEditBox;

    private EditBox inputEditBox;
    private Component statusMessage = Component.empty();

    private Component currentTitle;

    private float scale;
    private int vWidth;
    private int vHeight;
    private int vSidebarWidth;
    private int vContentXCenter;

    public SkinClosetScreen() {
        super(Component.translatable("gui.skincloset.title"));
        this.skinProfiles = SkinCache.getProfiles();
        this.currentTitle = this.title;
    }


    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        this.scale = (float) this.height / REFERENCE_HEIGHT;

        this.vWidth = (int)(this.width / this.scale);
        this.vHeight = (int)(this.height / this.scale);

        this.vSidebarWidth = V_SIDEBAR_WIDTH;

        this.vContentXCenter = this.vSidebarWidth + ((this.vWidth - this.vSidebarWidth) / 2);

        this.skinProfiles = SkinCache.getProfiles();
        this.totalPages = (int) Math.ceil((double) this.skinProfiles.size() / this.skinsPerPage);

        buildSidebarWidgets();

        switch (this.currentState) {
            case LIST:
                this.currentTitle = Component.translatable("gui.skincloset.title");
                buildListWidgets();
                break;
            case EDITING:
                if (this.selectedProfile == null) {
                    this.currentState = State.LIST;
                    this.init();
                    return;
                }
                this.currentTitle = Component.translatable("gui.skincloset.editing", this.selectedProfile.getName());
                buildEditingWidgets();
                break;
            case ADD_BY_NAME:
                this.currentTitle = Component.translatable("gui.skincloset.add_by_name");
                buildAddByNameWidgets();
                break;
            case ADD_FROM_LOCAL:
                this.currentTitle = Component.translatable("gui.skincloset.add_from_local");
                buildAddFromLocalWidgets();
                break;
        }
    }
    private void sendFeedback(Component message, NotificationCategory category) {
        if (SkinCloset.isModernNotificationLoaded) {
            ModernNotificationCompat.sendNotification(category, message);
        } else {
            if (category == NotificationCategory.FAILURE) {
                this.statusMessage = message.copy().withStyle(ChatFormatting.RED);
            } else if (category == NotificationCategory.SYSTEM) {
                this.statusMessage = message.copy().withStyle(ChatFormatting.YELLOW);
            } else {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(message, false);
                }
            }
        }
    }

    private void buildSidebarWidgets() {
        int xPos = V_PADDING;
        int yPos = V_TOP_MARGIN;
        int width = this.vSidebarWidth - (V_PADDING * 2);
        int height = V_BUTTON_HEIGHT;
        int yGap = V_BUTTON_HEIGHT + 10;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_my_skin"), (button) -> {
            addPlayerCurrentSkin();
        }).bounds(xPos, yPos, width, height).build());

        yPos += yGap;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_by_name"), (button) -> {
            this.currentState = State.ADD_BY_NAME;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(xPos, yPos, width, height).build());

        yPos += yGap;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.add_from_local"), (button) -> {
            this.currentState = State.ADD_FROM_LOCAL;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(xPos, yPos, width, height).build());
    }

    private void buildListWidgets() {

        int skinEntryHeight = V_NAME_TAG_HEIGHT + V_PREVIEW_HEIGHT + V_PREVIEW_TO_EDIT_GAP + V_BUTTON_HEIGHT + V_EDIT_TO_APPLY_GAP + V_BUTTON_HEIGHT;
        int totalGridWidth = (V_PREVIEW_WIDTH * SKIN_GRID_COLS) + (V_PADDING * (SKIN_GRID_COLS - 1));
        int gridStartY = V_TOP_MARGIN + 10;
        int gridStartX = this.vContentXCenter - (totalGridWidth / 2);
        int skinWidthWithPadding = V_PREVIEW_WIDTH + V_PADDING;

        int startIndex = currentPage * skinsPerPage;
        for (int i = 0; i < skinsPerPage; i++) {
            int profileIndex = startIndex + i;
            if (profileIndex >= this.skinProfiles.size()) break;

            SkinProfile profile = this.skinProfiles.get(profileIndex);
            int col = i % SKIN_GRID_COLS;
            int xPos = gridStartX + (col * skinWidthWithPadding);
            int yPos = gridStartY;
            int previewY = yPos + V_NAME_TAG_HEIGHT;

            this.addRenderableWidget(new PlayerPreviewWidget(xPos, previewY, V_PREVIEW_WIDTH, V_PREVIEW_HEIGHT, profile));

            int editY = previewY + V_PREVIEW_HEIGHT + V_PREVIEW_TO_EDIT_GAP;
            int applyY = editY + V_BUTTON_HEIGHT + V_EDIT_TO_APPLY_GAP;

            this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.edit"), (button) -> {
                this.selectedProfile = profile;
                this.currentState = State.EDITING;
                this.init();
            }).bounds(xPos, editY, V_PREVIEW_WIDTH, V_BUTTON_HEIGHT).build());

            this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.apply"), (button) -> {
                applySkin(profile);
            }).bounds(xPos, applyY, V_PREVIEW_WIDTH, V_BUTTON_HEIGHT).build());
        }
        int paginationY = this.vHeight - V_BUTTON_HEIGHT - 10;
        int paginationButtonWidth = 80;
        int pageTextWidth = 80;
        int prevButtonX = this.vContentXCenter - (pageTextWidth/2) - 5 - paginationButtonWidth;
        int nextButtonX = this.vContentXCenter + (pageTextWidth/2) + 5;

        if (currentPage > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("< Prev"), (button) -> {
                this.currentPage--;
                this.init();
            }).bounds(prevButtonX, paginationY, paginationButtonWidth, V_BUTTON_HEIGHT).build());
        }
        if (currentPage < totalPages - 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Next >"), (button) -> {
                this.currentPage++;
                this.init();
            }).bounds(nextButtonX, paginationY, paginationButtonWidth, V_BUTTON_HEIGHT).build());
        }
    }

    private void buildEditingWidgets() {
        int previewWidth = V_EDIT_PREVIEW_WIDTH;
        int previewHeight = V_EDIT_PREVIEW_HEIGHT;
        int optionsWidth = V_EDIT_OPTIONS_WIDTH;

        int totalWidth = previewWidth + V_PADDING + optionsWidth;
        int groupX = this.vContentXCenter - (totalWidth / 2);

        int previewX = groupX;
        int optionsX = groupX + previewWidth + V_PADDING;
        int groupY = V_TOP_MARGIN + 10;
        int previewY = groupY + V_NAME_TAG_HEIGHT;

        int optionsY = previewY;

        this.addRenderableWidget(new PlayerPreviewWidget(previewX, previewY, previewWidth, previewHeight, selectedProfile));

        this.nameEditBox = new EditBox(this.font, optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT, Component.translatable("gui.skincloset.profile_name"));
        this.nameEditBox.setValue(selectedProfile.getName());
        this.addRenderableWidget(this.nameEditBox);

        optionsY += V_BUTTON_HEIGHT + 10;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.save_name"), (button) -> {
            saveName();
        }).bounds(optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT).build());

        optionsY += V_BUTTON_HEIGHT + 10;

        Component modelButtonText = Component.translatable(selectedProfile.isSlim() ? "gui.skincloset.model_slim" : "gui.skincloset.model_classic");
        this.addRenderableWidget(Button.builder(modelButtonText, (button) -> {
            toggleModel();
        }).bounds(optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT).build());

        optionsY += V_BUTTON_HEIGHT + 15;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.apply"), (button) -> {
            applySkin(selectedProfile);
        }).bounds(optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT).build());

        optionsY += V_BUTTON_HEIGHT + 15;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.download"), (button) -> {
            downloadSkin();
        }).bounds(optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT).build());

        optionsY += V_BUTTON_HEIGHT + 15;

        Button deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.delete"), (button) -> {
                    SkinCache.removeProfile(selectedProfile);
                    SkinCache.saveProfiles();
                    this.selectedProfile = null;
                    this.currentState = State.LIST;
                    this.init();
                }).bounds(optionsX, optionsY, optionsWidth, V_BUTTON_HEIGHT)
                .build());
        deleteButton.setMessage(Component.translatable("gui.skincloset.delete").withStyle(s -> s.withColor(0xFF5555)));
        int optionsHeight = (optionsY + V_BUTTON_HEIGHT) - groupY;
        int groupHeight = Math.max(previewHeight + V_NAME_TAG_HEIGHT, optionsHeight);
        int backButtonY = groupY + groupHeight + 10;

        int maxButtonY = this.vHeight - V_BUTTON_HEIGHT - 10;
        if (backButtonY > maxButtonY) {
            backButtonY = maxButtonY;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.selectedProfile = null;
            this.currentState = State.LIST;
            this.init();
        }).bounds(optionsX, backButtonY, optionsWidth, V_BUTTON_HEIGHT).build());
    }

    private void buildAddFromLocalWidgets() {
        int width = 200;
        int height = V_BUTTON_HEIGHT;
        int contentX = this.vContentXCenter - (width / 2);
        int contentY = (this.vHeight / 2) - (height * 2);

        this.inputEditBox = new EditBox(this.font, contentX, contentY, width, height, Component.translatable("gui.skincloset.profile_name"));
        this.inputEditBox.setHint(Component.translatable("gui.skincloset.local.hint"));
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.browse"), (button) -> {
            openFilePicker();
        }).bounds(contentX, contentY + height + 5, width, height).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(contentX, contentY + (height + 5) * 2, width, height).build());
    }

    private void buildAddByNameWidgets() {
        int width = 200;
        int height = V_BUTTON_HEIGHT;
        int contentX = this.vContentXCenter - (width / 2);
        int contentY = (this.vHeight / 2) - (height * 2);

        this.inputEditBox = new EditBox(this.font, contentX, contentY, width, height, Component.translatable("gui.skincloset.username"));
        this.addRenderableWidget(this.inputEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.fetch"), (button) -> {
            fetchSkinByName(this.inputEditBox.getValue());
        }).bounds(contentX, contentY + height + 5, width, height).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.skincloset.back"), (button) -> {
            this.currentState = State.LIST;
            this.statusMessage = Component.empty();
            this.init();
        }).bounds(contentX, contentY + (height + 5) * 2, width, height).build());
    }

    private void toggleModel() {
        if (this.selectedProfile == null) return;
        String newModel = this.selectedProfile.isSlim() ? "classic" : "slim";
        this.selectedProfile.setModel(newModel);
        SkinCache.saveProfiles();
        sendFeedback(Component.translatable("gui.skincloset.model_switched", newModel), NotificationCategory.SUCCESS);
        this.init();
    }

    private void openFilePicker() {
        String profileName = this.inputEditBox.getValue();
        if (profileName == null || profileName.trim().isEmpty()) {
            sendFeedback(Component.translatable("gui.skincloset.error.no_profile_name"), NotificationCategory.FAILURE);
            return;
        }

        this.inputEditBox.setEditable(false);
        this.renderables.forEach(w -> { if (w instanceof Button b) b.active = false; });

        new Thread(() -> {
            String selectedFilePath = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();

                selectedFilePath = TinyFileDialogs.tinyfd_openFileDialog(
                        Component.translatable("gui.skincloset.browse.title").getString(),
                        null,
                        filters,
                        "PNG Files (*.png)",
                        false
                );
            }

            if (selectedFilePath != null) {
                Path localSkinPath = Path.of(selectedFilePath);
                this.minecraft.execute(() -> {
                    uploadSkinFromLocal(profileName, localSkinPath);
                });
            } else {
                this.minecraft.execute(this::init);
            }
        }).start();
    }

    private void applySkin(SkinProfile profile) {
        if (profile == null) return;
        Optional<SkinProfile.SkinData> data = profile.getSkinData();
        if (data.isPresent()) {
            PacketRegistry.CHANNEL.sendToServer(new C2SChangeSkinPacket(data.get().value(), data.get().signature()));
            LastSkinCache.save(profile);
            sendFeedback(Component.translatable("gui.skincloset.applied", profile.getName()), NotificationCategory.SUCCESS);
            this.onClose();
        }
    }

    private void saveName() {
        if (this.selectedProfile == null || this.nameEditBox == null) return;
        String newName = this.nameEditBox.getValue();
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(selectedProfile.getName())) {
            selectedProfile.setName(newName);
            SkinCache.saveProfiles();
            sendFeedback(Component.translatable("gui.skincloset.name_saved", newName), NotificationCategory.SUCCESS);
            this.currentTitle = Component.translatable("gui.skincloset.editing", this.selectedProfile.getName());
        }
    }

    private void addPlayerCurrentSkin() {
        try {
            GameProfile profile = this.minecraft.player.getGameProfile();
            String username = profile.getName();

            if (username == null || username.trim().isEmpty()) {
                sendFeedback(Component.translatable("gui.skincloset.error.self_no_skin"), NotificationCategory.FAILURE);
                return;
            }
            sendFeedback(Component.translatable("gui.skincloset.fetching"), NotificationCategory.SYSTEM);
            this.renderables.forEach(w -> { if (w instanceof Button b) b.active = false; });

            SkinDownloader.fetchSkinByUsername(username, (newProfile) -> {
                if (newProfile != null) {
                    SkinCache.addProfile(newProfile);
                    SkinCache.saveProfiles();
                    this.minecraft.execute(() -> {
                        this.currentState = State.LIST;
                        this.init();
                        sendFeedback(Component.translatable("gui.skincloset.success.added_self"), NotificationCategory.SUCCESS);
                    });
                } else {
                    this.minecraft.execute(() -> {
                        sendFeedback(Component.translatable("gui.skincloset.error.self_no_skin"), NotificationCategory.FAILURE);
                        this.init();
                    });
                }
            });

        } catch (Exception e) {
            SkinCloset.LOGGER.error("Failed to get player username for addPlayerCurrentSkin", e);
            sendFeedback(Component.translatable("gui.skincloset.error.self_no_skin"), NotificationCategory.FAILURE);
            this.init();
        }
    }

    private void fetchSkinByName(String username) {
        if (username == null || username.trim().isEmpty()) {
            sendFeedback(Component.translatable("gui.skincloset.error.no_name"), NotificationCategory.FAILURE);
            return;
        }
        sendFeedback(Component.translatable("gui.skincloset.fetching"), NotificationCategory.SYSTEM);
        if(this.inputEditBox != null) {
            this.inputEditBox.setEditable(false);
        }
        this.renderables.forEach(w -> { if (w instanceof Button b) b.active = false; });

        SkinDownloader.fetchSkinByUsername(username, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.minecraft.execute(() -> {
                    this.currentState = State.LIST;
                    this.init();
                    sendFeedback(Component.translatable("gui.skincloset.success.added", profile.getName()), NotificationCategory.SUCCESS);
                });
            } else {
                this.minecraft.execute(() -> {
                    sendFeedback(Component.translatable("gui.skincloset.error.not_found", username), NotificationCategory.FAILURE);
                    this.init();
                });
            }
        });
    }

    private void uploadSkinFromLocal(String profileName, Path localSkinPath) {
        if (profileName == null || profileName.trim().isEmpty()) {
            sendFeedback(Component.translatable("gui.skincloset.error.no_profile_name"), NotificationCategory.FAILURE);
            return;
        }

        if (!Files.exists(localSkinPath)) {
            sendFeedback(Component.translatable("gui.skincloset.error.local_not_found_generic"), NotificationCategory.FAILURE);
            return;
        }
        sendFeedback(Component.translatable("gui.skincloset.uploading"), NotificationCategory.SYSTEM);
        if (this.inputEditBox != null) {
            this.inputEditBox.setEditable(false);
        }
        this.renderables.forEach(w -> { if (w instanceof Button b) b.active = false; });


        SkinDownloader.uploadSkinFromLocal(profileName, localSkinPath, (profile) -> {
            if (profile != null) {
                SkinCache.addProfile(profile);
                SkinCache.saveProfiles();
                this.minecraft.execute(() -> {
                    this.currentState = State.LIST;
                    this.init();
                    sendFeedback(Component.translatable("gui.skincloset.success.uploaded", profile.getName()), NotificationCategory.SUCCESS);
                });
            } else {
                this.minecraft.execute(() -> {
                    sendFeedback(Component.translatable("gui.skincloset.error.upload_failed"), NotificationCategory.FAILURE);
                    this.currentState = State.ADD_FROM_LOCAL;
                    this.init();
                });
            }
        });
    }

    private void downloadSkin() {
        if (this.selectedProfile == null) return;
        Optional<String> urlOpt = this.selectedProfile.getTextureUrl();
        if (urlOpt.isEmpty()) {
            sendFeedback(Component.translatable("gui.skincloset.error.no_texture"), NotificationCategory.FAILURE);
            return;
        }

        Path downloadsDir = Path.of(System.getProperty("user.home"), "Downloads");
        String fileName = this.selectedProfile.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
        Path targetPath = downloadsDir.resolve(fileName);

        SkinDownloader.downloadSkinTexture(urlOpt.get(), targetPath, (success) -> {
            if (success) {
                sendFeedback(Component.translatable("gui.skincloset.success.downloaded", fileName), NotificationCategory.SUCCESS);
            } else {
                sendFeedback(Component.translatable("gui.skincloset.error.download_failed"), NotificationCategory.FAILURE);
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.pose().pushPose();
        graphics.pose().scale(this.scale, this.scale, this.scale);

        int mouseXScaled = (int) (mouseX / this.scale);
        int mouseYScaled = (int) (mouseY / this.scale);

        graphics.fill(0, 0, this.vSidebarWidth, this.vHeight, 0x80000000);

        super.render(graphics, mouseXScaled, mouseYScaled, partialTick);

        int titleX = (this.vSidebarWidth + this.vWidth) / 2;
        int titleY = 10;

        graphics.pose().pushPose();
        graphics.pose().scale(TITLE_TEXT_SCALE, TITLE_TEXT_SCALE, 1.0f);
        graphics.drawCenteredString(this.font, this.currentTitle, (int)(titleX / TITLE_TEXT_SCALE), (int)(titleY / TITLE_TEXT_SCALE), 0xFFFFFF);
        graphics.pose().popPose();


        if (this.currentState == State.LIST) {
            renderSkinNames(graphics);
        } else if (this.currentState == State.EDITING && this.selectedProfile != null) {
            renderEditingSkinName(graphics);
        }
        if (!SkinCloset.isModernNotificationLoaded && (this.currentState == State.ADD_BY_NAME || this.currentState == State.ADD_FROM_LOCAL)) {
            int statusY = (this.vHeight / 2) + 50;
            graphics.drawCenteredString(this.font, this.statusMessage, this.vContentXCenter, statusY, 0xFFFF55);
        }

        if (this.currentState == State.LIST && this.totalPages > 0) {
            String pageText = Component.translatable("gui.skincloset.page", this.currentPage + 1, this.totalPages).getString();
            int paginationY = this.vHeight - V_BUTTON_HEIGHT;

            graphics.pose().pushPose();
            graphics.pose().scale(GENERAL_TEXT_SCALE, GENERAL_TEXT_SCALE, 1.0f);
            graphics.drawCenteredString(this.font, pageText, (int)(this.vContentXCenter / GENERAL_TEXT_SCALE), (int)(paginationY / GENERAL_TEXT_SCALE), 0xFFFFFF);
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    private void renderSkinNames(GuiGraphics graphics) {
        int totalGridWidth = (V_PREVIEW_WIDTH * SKIN_GRID_COLS) + (V_PADDING * (SKIN_GRID_COLS - 1));
        int gridStartY = V_TOP_MARGIN + 10;
        int gridStartX = this.vContentXCenter - (totalGridWidth / 2);
        int skinWidthWithPadding = V_PREVIEW_WIDTH + V_PADDING;

        int startIndex = currentPage * skinsPerPage;
        for (int i = 0; i < skinsPerPage; i++) {
            int profileIndex = startIndex + i;
            if (profileIndex >= this.skinProfiles.size()) break;

            SkinProfile profile = this.skinProfiles.get(profileIndex);
            int col = i % SKIN_GRID_COLS;
            int xPos = gridStartX + (col * skinWidthWithPadding);
            int yPos = gridStartY;

            graphics.pose().pushPose();
            graphics.pose().scale(GENERAL_TEXT_SCALE, GENERAL_TEXT_SCALE, 1.0f);
            graphics.drawCenteredString(
                    this.font,
                    profile.getName(),
                    (int)((xPos + (V_PREVIEW_WIDTH / 2)) / GENERAL_TEXT_SCALE),
                    (int)((yPos + 50) / GENERAL_TEXT_SCALE),
                    0xFFFFFF
            );
            graphics.pose().popPose();
        }
    }

    private void renderEditingSkinName(GuiGraphics graphics) {
        int previewWidth = V_EDIT_PREVIEW_WIDTH;
        int totalWidth = previewWidth + V_PADDING + V_EDIT_OPTIONS_WIDTH;
        int groupX = this.vContentXCenter - (totalWidth / 2);
        int previewX = groupX;
        int groupY = V_TOP_MARGIN + 10;
        int nameY = groupY;

        graphics.pose().pushPose();
        graphics.pose().scale(GENERAL_TEXT_SCALE, GENERAL_TEXT_SCALE, 1.0f);
        graphics.drawCenteredString(
                this.font,
                this.selectedProfile.getName(),
                (int)((previewX + (previewWidth / 2)) / GENERAL_TEXT_SCALE),
                (int)((nameY + 50) / GENERAL_TEXT_SCALE),
                0xFFFFFF
        );
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        return super.mouseClicked(pMouseX / this.scale, pMouseY / this.scale, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        return super.mouseReleased(pMouseX / this.scale, pMouseY / this.scale, pButton);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        return super.mouseDragged(pMouseX / this.scale, pMouseY / this.scale, pButton, pDragX / this.scale, pDragY / this.scale);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        return super.mouseScrolled(pMouseX / this.scale, pMouseY / this.scale, pDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}