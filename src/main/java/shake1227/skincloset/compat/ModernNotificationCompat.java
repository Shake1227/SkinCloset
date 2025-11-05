package shake1227.skincloset.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public class ModernNotificationCompat {
    public static void sendNotification(shake1227.modernnotification.core.NotificationCategory category, Component message) {

        try {
            shake1227.modernnotification.notification.Notification notification =
                    new shake1227.modernnotification.notification.Notification(
                            shake1227.modernnotification.core.NotificationType.LEFT,
                            category,
                            null,
                            java.util.List.of(message),
                            -1
                    );
            shake1227.modernnotification.notification.NotificationManager.getInstance().getRenderer().calculateDynamicWidth(notification);

            shake1227.modernnotification.notification.NotificationManager.getInstance().addNotification(notification);

        } catch (Throwable t) {
            sendFallbackMessage(message, category);
        }
    }
    public static void sendFallbackMessage(Component message, shake1227.modernnotification.core.NotificationCategory category) {
        if (Minecraft.getInstance().player == null) return;

        if (category == shake1227.modernnotification.core.NotificationCategory.FAILURE) {
            Minecraft.getInstance().player.displayClientMessage(message.copy().withStyle(net.minecraft.ChatFormatting.RED), false);
        } else {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}