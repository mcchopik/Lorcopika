package lorcopika;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoreBindingStore {
	private final JavaPlugin plugin;
	private final File file;
	private final Map<UUID, String> bindings = new LinkedHashMap<>();
	private final Map<UUID, String> names = new LinkedHashMap<>();

	public LoreBindingStore(JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "bindings.yml");
	}

	public void load() {
		bindings.clear();
		names.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection players = yaml.getConfigurationSection("players");
		if (players == null) {
			return;
		}
		for (String rawUuid : players.getKeys(false)) {
			try {
				UUID uuid = UUID.fromString(rawUuid);
				String profile = players.getString(rawUuid + ".profile");
				String name = players.getString(rawUuid + ".name");
				if (profile != null && !profile.isBlank()) {
					bindings.put(uuid, profile);
				}
				if (name != null && !name.isBlank()) {
					names.put(uuid, name);
				}
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Invalid UUID in bindings.yml: " + rawUuid);
			}
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (UUID uuid : bindings.keySet()) {
			yaml.set("players." + uuid + ".profile", bindings.get(uuid));
			yaml.set("players." + uuid + ".name", names.get(uuid));
		}
		try {
			yaml.save(file);
		} catch (IOException exception) {
			plugin.getLogger().warning("Could not save bindings.yml: " + exception.getMessage());
		}
	}

	public String profileFor(UUID uuid) {
		return bindings.get(uuid);
	}

	public String nameFor(UUID uuid) {
		return names.get(uuid);
	}

	public UUID uuidForName(String name) {
		for (Map.Entry<UUID, String> entry : names.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void remember(UUID uuid, String name) {
		if (name != null && !name.isBlank()) {
			names.put(uuid, name);
		}
	}

	public void bind(UUID uuid, String profile, String name) {
		bindings.put(uuid, profile);
		remember(uuid, name);
	}

	public boolean unbind(UUID uuid) {
		return bindings.remove(uuid) != null;
	}

	public Map<UUID, String> all() {
		return new LinkedHashMap<>(bindings);
	}
}
