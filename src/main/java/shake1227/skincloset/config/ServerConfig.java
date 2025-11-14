package shake1227.skincloset.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import java.util.List;
import java.util.Collections;

public class ServerConfig {

    public static final ServerConfig INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
                .configure(ServerConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    public final ForgeConfigSpec.ConfigValue<List<? extends String>> consoleCommand;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerCommand;

    ServerConfig(ForgeConfigSpec.Builder builder) {
        builder.push("Commands");

        consoleCommand = builder
                .comment("List of commands to be executed by the console when a player changes their skin.",
                        "Use @p for player name and @u for player UUID.")
                .defineList("consoleCommand", Collections.emptyList(), obj -> obj instanceof String);

        playerCommand = builder
                .comment("List of commands to be executed by the player when they change their skin.",
                        "Use @p for player name and @u for player UUID.")
                .defineList("playerCommand", Collections.emptyList(), obj -> obj instanceof String);

        builder.pop();
    }
}