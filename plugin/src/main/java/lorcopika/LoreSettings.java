package lorcopika;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoreSettings {
	public static final Set<String> FEATURES = new LinkedHashSet<>(Arrays.asList(
		"fragile_body",
		"warden_ignore",
		"fire_weakness",
		"loud_sounds",
		"random_darkness",
		"infected_zone",
		"nether_sickness",
		"sense",
		"sculk_spread",
		"memories",
		"familiar_sound"
	));

	public static final Set<String> PARAMS = new LinkedHashSet<>(Arrays.asList(
		"fragile_body.max_health",
		"fire_weakness.burn_multiplier",
		"fire_weakness.damage_multiplier",
		"loud_sounds.damage",
		"loud_sounds.radius",
		"loud_sounds.interval",
		"random_darkness.min_interval",
		"random_darkness.max_interval",
		"random_darkness.duration",
		"infected_zone.chance",
		"infected_zone.secondary_multiplier",
		"infected_zone.height_ratio",
		"nether_sickness.start_seconds",
		"nether_sickness.effect_interval",
		"sense.radius",
		"sense.mode",
		"sculk_spread.radius",
		"sculk_spread.max_blocks",
		"memories.min_interval",
		"memories.max_interval",
		"familiar_sound.slow_duration",
		"familiar_sound.slow_amplifier"
	));

	private final JavaPlugin plugin;
	private final Map<String, Boolean> features = new LinkedHashMap<>();
	private final Map<String, Double> params = new LinkedHashMap<>();
	private final Map<UUID, PlayerSettings> players = new LinkedHashMap<>();
	private boolean debug;

	public LoreSettings(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		debug = plugin.getConfig().getBoolean("debug", false);
		features.clear();
		params.clear();
		players.clear();
		for (String feature : FEATURES) {
			features.put(feature, plugin.getConfig().getBoolean("features." + feature, true));
		}
		for (String param : PARAMS) {
			double value = plugin.getConfig().getDouble("params." + param, defaultParam(param));
			params.put(param, Double.isFinite(value) ? value : defaultParam(param));
		}
		ConfigurationSection playerSection = plugin.getConfig().getConfigurationSection("players");
		if (playerSection != null) {
			for (String rawUuid : playerSection.getKeys(false)) {
				try {
					UUID uuid = UUID.fromString(rawUuid);
					PlayerSettings settings = new PlayerSettings();
					ConfigurationSection section = playerSection.getConfigurationSection(rawUuid);
					if (section == null) {
						continue;
					}
					ConfigurationSection featureSection = section.getConfigurationSection("features");
					if (featureSection != null) {
						for (String key : featureSection.getKeys(false)) {
							settings.features.put(key, featureSection.getBoolean(key));
						}
					}
					ConfigurationSection paramSection = section.getConfigurationSection("params");
					if (paramSection != null) {
						for (String key : paramSection.getKeys(true)) {
							if (paramSection.isDouble(key) || paramSection.isInt(key) || paramSection.isLong(key)) {
								settings.params.put(key, paramSection.getDouble(key));
							}
						}
					}
					if (section.contains("debug")) {
						settings.debug = section.getBoolean("debug");
					}
					players.put(uuid, settings);
				} catch (IllegalArgumentException exception) {
					plugin.getLogger().warning("Invalid player UUID in config: " + rawUuid);
				}
			}
		}
	}

	public void save() {
		plugin.getConfig().set("debug", debug);
		for (String feature : FEATURES) {
			plugin.getConfig().set("features." + feature, features.getOrDefault(feature, true));
		}
		for (String param : PARAMS) {
			plugin.getConfig().set("params." + param, params.getOrDefault(param, defaultParam(param)));
		}
		plugin.getConfig().set("players", null);
		for (Map.Entry<UUID, PlayerSettings> entry : players.entrySet()) {
			String base = "players." + entry.getKey();
			PlayerSettings settings = entry.getValue();
			settings.features.forEach((key, value) -> plugin.getConfig().set(base + ".features." + key, value));
			settings.params.forEach((key, value) -> plugin.getConfig().set(base + ".params." + key, value));
			if (settings.debug != null) {
				plugin.getConfig().set(base + ".debug", settings.debug);
			}
		}
		plugin.saveConfig();
	}

	public boolean feature(UUID uuid, String feature) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.features.containsKey(feature)) {
			return settings.features.get(feature);
		}
		return features.getOrDefault(feature, true);
	}

	public double param(UUID uuid, String param) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.params.containsKey(param)) {
			double value = settings.params.get(param);
			if (Double.isFinite(value)) {
				return value;
			}
		}
		return params.getOrDefault(param, defaultParam(param));
	}

	public boolean debug(UUID uuid) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.debug != null) {
			return settings.debug;
		}
		return debug;
	}

	public void setFeature(UUID uuid, String feature, boolean value) {
		if (uuid == null) {
			features.put(feature, value);
		} else {
			player(uuid).features.put(feature, value);
		}
	}

	public void setParam(UUID uuid, String param, double value) {
		if (uuid == null) {
			params.put(param, value);
		} else {
			player(uuid).params.put(param, value);
		}
	}

	public void reset(UUID uuid) {
		if (uuid == null) {
			features.clear();
			params.clear();
			players.clear();
			debug = false;
			FEATURES.forEach(feature -> features.put(feature, true));
			PARAMS.forEach(param -> params.put(param, defaultParam(param)));
		} else {
			players.remove(uuid);
		}
	}

	public void setDebug(boolean value) {
		debug = value;
	}

	public void setDebug(UUID uuid, Boolean value) {
		if (uuid == null) {
			debug = value != null && value;
		} else {
			player(uuid).debug = value;
		}
	}

	private PlayerSettings player(UUID uuid) {
		return players.computeIfAbsent(uuid, key -> new PlayerSettings());
	}

	public static double defaultParam(String param) {
		return switch (param) {
			case "fragile_body.max_health" -> 14.0;
			case "fire_weakness.burn_multiplier" -> 1.5;
			case "fire_weakness.damage_multiplier" -> 1.25;
			case "loud_sounds.damage" -> 4.0;
			case "loud_sounds.radius" -> 16.0;
			case "loud_sounds.interval" -> 40.0;
			case "random_darkness.min_interval" -> 1200.0;
			case "random_darkness.max_interval" -> 3600.0;
			case "random_darkness.duration" -> 200.0;
			case "infected_zone.chance" -> 0.5;
			case "infected_zone.secondary_multiplier" -> 0.4;
			case "infected_zone.height_ratio" -> 0.6;
			case "nether_sickness.start_seconds" -> 60.0;
			case "nether_sickness.effect_interval" -> 200.0;
			case "sense.radius" -> 16.0;
			case "sense.mode" -> 0.0;
			case "sculk_spread.radius" -> 10.0;
			case "sculk_spread.max_blocks" -> 12.0;
			case "memories.min_interval" -> 2400.0;
			case "memories.max_interval" -> 7200.0;
			case "familiar_sound.slow_duration" -> 60.0;
			case "familiar_sound.slow_amplifier" -> 1.0;
			default -> 0.0;
		};
	}

	public static final class PlayerSettings {
		public final Map<String, Boolean> features = new LinkedHashMap<>();
		public final Map<String, Double> params = new LinkedHashMap<>();
		public Boolean debug;
	}
}
