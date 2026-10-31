package lorcopika;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import lorcopika.binding.BindingStore;
import lorcopika.config.LoreConfig;
import lorcopika.mechanic.LoreMechanics;
import lorcopika.profile.CharacterProfile;
import lorcopika.profile.ProfileStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class LoreState {
	private static LoreState instance;

	private final MinecraftServer server;
	private final Path root;
	private final Logger logger;
	private final Map<UUID, Runtime> runtimes = new ConcurrentHashMap<>();
	private LoreConfig config;
	private ProfileStore profiles;
	private BindingStore bindings;

	private LoreState(MinecraftServer server) {
		this.server = server;
		this.logger = Logger.getLogger("Lorcopika");
		this.root = FabricLoader.getInstance().getConfigDir().resolve("lorcopika");
		try {
			Files.createDirectories(root.resolve("characters"));
		} catch (IOException exception) {
			logger.warning("Could not create config directory: " + exception.getMessage());
		}
		this.config = LoreConfig.load(root.resolve("config.json"), logger);
		this.profiles = new ProfileStore(root.resolve("characters"), logger);
		this.profiles.load();
		this.profiles.ensureDefault();
		this.bindings = new BindingStore(root.resolve("bindings.json"), logger);
		this.bindings.load();
	}

	public static synchronized LoreState get(MinecraftServer server) {
		if (instance == null || instance.server != server) {
			instance = new LoreState(server);
		}
		return instance;
	}

	public static LoreState peek() {
		return instance;
	}

	public static LoreState peek(MinecraftServer server) {
		return instance != null && instance.server == server ? instance : null;
	}

	public MinecraftServer server() {
		return server;
	}

	public LoreConfig config() {
		return config;
	}

	public ProfileStore profiles() {
		return profiles;
	}

	public BindingStore bindings() {
		return bindings;
	}

	public Path root() {
		return root;
	}

	public Logger logger() {
		return logger;
	}

	public CharacterProfile profile(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		return profiles.get(bindings.profileFor(player.getUUID()));
	}

	public boolean isBound(ServerPlayer player) {
		return profile(player) != null;
	}

	public boolean feature(ServerPlayer player, String feature) {
		return isBound(player) && config.feature(player.getUUID().toString(), feature);
	}

	public double param(ServerPlayer player, String param) {
		return config.param(player.getUUID().toString(), param);
	}

	public boolean debug(ServerPlayer player) {
		return isBound(player) && config.debug(player.getUUID().toString());
	}

	public void debug(ServerPlayer player, String message) {
		if (debug(player)) {
			player.sendSystemMessage(Component.literal("[lore] " + message).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public Runtime runtime(ServerPlayer player) {
		return runtimes.computeIfAbsent(player.getUUID(), uuid -> new Runtime());
	}

	public void saveConfig() {
		config.save(root.resolve("config.json"), logger);
	}

	public void saveProfiles() {
		profiles.all().values().forEach(profiles::save);
	}

	public void reload() {
		config = LoreConfig.load(root.resolve("config.json"), logger);
		profiles.reload();
		bindings.reload();
		LoreMechanics.refreshAll(server);
	}

	public static final class Runtime {
		public long nextDarkness;
		public long nextMemory;
		public long nextLoud;
		public long nextFamiliar;
		public long nextNetherEffect;
		public long netherTicks;
		public boolean fireExtended;
		public boolean secondary;
	}
}
