package lorcopika.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class LoreConfig {
	public static final String[] FEATURES = {
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
		"familiar_sound",
		"eye_darkness",
		"infected_hand"
	};

	public static final String[] PARAMS = {
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
	};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public boolean debug;
	public Map<String, Boolean> features = new LinkedHashMap<>();
	public Map<String, Double> params = new LinkedHashMap<>();
	public Map<String, PlayerSettings> players = new LinkedHashMap<>();

	public LoreConfig() {
		features = defaultFeatures();
		params = defaultParams();
		players = new LinkedHashMap<>();
		debug = false;
	}

	public static Map<String, Boolean> defaultFeatures() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		for (String feature : FEATURES) {
			map.put(feature, true);
		}
		return map;
	}

	public static Map<String, Double> defaultParams() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("fragile_body.max_health", 14.0);
		map.put("fire_weakness.burn_multiplier", 1.5);
		map.put("fire_weakness.damage_multiplier", 1.25);
		map.put("loud_sounds.damage", 4.0);
		map.put("loud_sounds.radius", 16.0);
		map.put("loud_sounds.interval", 40.0);
		map.put("random_darkness.min_interval", 1200.0);
		map.put("random_darkness.max_interval", 3600.0);
		map.put("random_darkness.duration", 200.0);
		map.put("infected_zone.chance", 0.5);
		map.put("infected_zone.secondary_multiplier", 0.4);
		map.put("infected_zone.height_ratio", 0.6);
		map.put("nether_sickness.start_seconds", 60.0);
		map.put("nether_sickness.effect_interval", 200.0);
		map.put("sense.radius", 16.0);
		map.put("sense.mode", 0.0);
		map.put("sculk_spread.radius", 10.0);
		map.put("sculk_spread.max_blocks", 12.0);
		map.put("memories.min_interval", 2400.0);
		map.put("memories.max_interval", 7200.0);
		map.put("familiar_sound.slow_duration", 60.0);
		map.put("familiar_sound.slow_amplifier", 1.0);
		return map;
	}

	public static LoreConfig load(Path file, Logger logger) {
		LoreConfig defaults = new LoreConfig();
		if (!Files.exists(file)) {
			defaults.save(file, logger);
			return defaults;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			LoreConfig loaded = GSON.fromJson(reader, LoreConfig.class);
			if (loaded == null) {
				throw new JsonParseException("empty config");
			}
			loaded.fillDefaults(defaults, logger);
			loaded.save(file, logger);
			return loaded;
		} catch (Exception exception) {
			logger.warning("Invalid config " + file + ", using defaults: " + exception.getMessage());
			return defaults;
		}
	}

	public void fillDefaults(LoreConfig defaults, Logger logger) {
		if (features == null) {
			features = new LinkedHashMap<>();
		}
		if (params == null) {
			params = new LinkedHashMap<>();
		}
		if (players == null) {
			players = new LinkedHashMap<>();
		}
		for (String feature : FEATURES) {
			if (!features.containsKey(feature) || features.get(feature) == null) {
				features.put(feature, defaults.features.get(feature));
			}
		}
		for (String param : PARAMS) {
			Double value = params.get(param);
			if (value == null || !Double.isFinite(value)) {
				if (value != null) {
					logger.warning("Invalid value for " + param + ", using default");
				}
				params.put(param, defaults.params.get(param));
			}
		}
		players.forEach((uuid, settings) -> {
			if (settings == null) {
				return;
			}
			if (settings.features == null) {
				settings.features = new LinkedHashMap<>();
			}
			if (settings.params == null) {
				settings.params = new LinkedHashMap<>();
			}
			settings.params.forEach((key, value) -> {
				if (value == null || !Double.isFinite(value)) {
					settings.params.put(key, defaults.params.getOrDefault(key, 0.0));
				}
			});
		});
	}

	public void save(Path file, Logger logger) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			logger.warning("Could not save config " + file + ": " + exception.getMessage());
		}
	}

	public boolean feature(String uuid, String feature) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.features != null && settings.features.containsKey(feature)) {
			Boolean value = settings.features.get(feature);
			if (value != null) {
				return value;
			}
		}
		return features.getOrDefault(feature, true);
	}

	public double param(String uuid, String param) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.params != null && settings.params.containsKey(param)) {
			Double value = settings.params.get(param);
			if (value != null && Double.isFinite(value)) {
				return value;
			}
		}
		Double value = params.get(param);
		return value == null || !Double.isFinite(value) ? defaultParams().getOrDefault(param, 0.0) : value;
	}

	public boolean debug(String uuid) {
		PlayerSettings settings = players.get(uuid);
		if (settings != null && settings.debug != null) {
			return settings.debug;
		}
		return debug;
	}

	public PlayerSettings player(String uuid) {
		return players.computeIfAbsent(uuid, key -> new PlayerSettings());
	}

	public void reset(String uuid) {
		if (uuid == null) {
			features = defaultFeatures();
			params = defaultParams();
			players.clear();
			debug = false;
		} else {
			players.remove(uuid);
		}
	}

	public static final class PlayerSettings {
		public Map<String, Boolean> features = new LinkedHashMap<>();
		public Map<String, Double> params = new LinkedHashMap<>();
		public Boolean debug;
	}
}
