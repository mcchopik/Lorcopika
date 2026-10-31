package lorcopika.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.server.level.ServerPlayer;

public final class BindingStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public Map<String, String> bindings = new LinkedHashMap<>();
	public Map<String, String> names = new LinkedHashMap<>();

	private final transient Path file;
	private final transient Logger logger;

	public BindingStore(Path file, Logger logger) {
		this.file = file;
		this.logger = logger;
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			BindingStore loaded = GSON.fromJson(reader, BindingStore.class);
			if (loaded != null) {
				bindings = loaded.bindings == null ? new LinkedHashMap<>() : loaded.bindings;
				names = loaded.names == null ? new LinkedHashMap<>() : loaded.names;
			}
		} catch (Exception exception) {
			logger.warning("Invalid bindings " + file + ": " + exception.getMessage());
			bindings = new LinkedHashMap<>();
			names = new LinkedHashMap<>();
		}
	}

	public void reload() {
		bindings = new LinkedHashMap<>();
		names = new LinkedHashMap<>();
		load();
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			logger.warning("Could not save bindings: " + exception.getMessage());
		}
	}

	public String profileFor(UUID uuid) {
		return uuid == null ? null : bindings.get(uuid.toString());
	}

	public void bind(UUID uuid, String name, String profile) {
		bindings.put(uuid.toString(), profile);
		names.put(uuid.toString(), name);
		save();
	}

	public boolean unbind(UUID uuid) {
		names.remove(uuid.toString());
		boolean removed = bindings.remove(uuid.toString()) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public UUID uuidForName(String name) {
		for (Map.Entry<String, String> entry : names.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				return UUID.fromString(entry.getKey());
			}
		}
		return null;
	}

	public void ensureDefault(ServerPlayer player) {
		if (bindings.containsKey(player.getUUID().toString())) {
			return;
		}
		String name = player.getGameProfile().name();
		names.put(player.getUUID().toString(), name);
		if (name.equalsIgnoreCase("McChopik")) {
			bindings.put(player.getUUID().toString(), "chopik");
		}
		save();
	}
}
