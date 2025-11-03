package shake1227.skincloset.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shake1227.skincloset.Constants;
import org.lwjgl.glfw.GLFW;

// 1.20.1: イベントバスへの自動登録アノテーションを追加
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Keybinds {
    public static final String KEY_CATEGORY_SKINCLOSET = "key.category.skincloset";
    public static final String KEY_OPEN_GUI = "key.skincloset.open_gui";

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            KEY_OPEN_GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, // Gキー
            KEY_CATEGORY_SKINCLOSET
    );

    // 1.20.1: @SubscribeEvent を追加し、イベントを引数に取る
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }
}

