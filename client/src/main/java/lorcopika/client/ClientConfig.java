package lorcopika.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ClientConfig instance;

	public boolean eyeDarkness = true;
	public boolean infectedHand = true;
	public double eyeDarknessAlpha = 0.65;
	public String defaultNickname = "McChopik";
	public String defaultProfile = "chopik";

	public static ClientConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ClientConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("lorcopika").resolve("client.json");
		ClientConfig defaults = new ClientConfig();
		if (!Files.exists(file)) {
			defaults.save(file);
			return defaults;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
			if (loaded == null) {
				return defaults;
			}
			if (!Double.isFinite(loaded.eyeDarknessAlpha) || loaded.eyeDarknessAlpha < 0.0 || loaded.eyeDarknessAlpha > 1.0) {
				loaded.eyeDarknessAlpha = defaults.eyeDarknessAlpha;
			}
			if (loaded.defaultNickname == null || loaded.defaultNickname.isBlank()) {
				loaded.defaultNickname = defaults.defaultNickname;
			}
			if (loaded.defaultProfile == null || loaded.defaultProfile.isBlank()) {
				loaded.defaultProfile = defaults.defaultProfile;
			}
			loaded.save(file);
			return loaded;
		} catch (Exception exception) {
			Logger.getLogger("Lorcopika").warning("Invalid client config, using defaults: " + exception.getMessage());
			return defaults;
		}
	}

	private void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			Logger.getLogger("Lorcopika").warning("Could not save client config: " + exception.getMessage());
		}
	}
}
