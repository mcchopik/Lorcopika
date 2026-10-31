package lorcopika;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoreCommand implements CommandExecutor, TabCompleter {
	private static final List<String> SUBCOMMANDS = List.of(
		"status",
		"bind",
		"unbind",
		"bindings",
		"profile",
		"memory",
		"trigger",
		"feature",
		"set",
		"show",
		"reload",
		"reset",
		"debug",
		"help"
	);
	private static final List<String> PROFILE_COMMANDS = List.of(
		"list",
		"create",
		"delete",
		"info",
		"name",
		"description",
		"memory",
		"familiar"
	);
	private static final List<String> LIST_COMMANDS = List.of("add", "remove", "list");
	private static final List<String> SOUNDS = List.of(
		"minecraft:entity.warden.ambient",
		"minecraft:entity.warden.sonic_boom",
		"minecraft:entity.warden.heartbeat",
		"minecraft:block.sculk_sensor.clicking",
		"minecraft:block.sculk_shrieker.shriek",
		"minecraft:item.goat_horn.sound."
	);

	private final JavaPlugin plugin;
	private final LoreSettings settings;
	private final LoreProfileStore profiles;
	private final LoreBindingStore bindings;
	private final LoreMechanics mechanics;

	public LoreCommand(JavaPlugin plugin, LoreSettings settings, LoreProfileStore profiles, LoreBindingStore bindings, LoreMechanics mechanics) {
		this.plugin = plugin;
		this.settings = settings;
		this.profiles = profiles;
		this.bindings = bindings;
		this.mechanics = mechanics;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			return status(sender);
		}
		return switch (args[0].toLowerCase(Locale.ROOT)) {
			case "status" -> status(sender);
			case "bind" -> bind(sender, args);
			case "unbind" -> unbind(sender, args);
			case "bindings" -> listBindings(sender);
			case "profile" -> profile(sender, args);
			case "memory" -> memory(sender, args);
			case "trigger" -> trigger(sender, args);
			case "feature" -> feature(sender, args);
			case "set" -> set(sender, args);
			case "show" -> show(sender, args);
			case "reload" -> reload(sender);
			case "reset" -> reset(sender, args);
			case "debug" -> debug(sender, args);
			case "help" -> help(sender);
			default -> {
				sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /lore help.");
				yield true;
			}
		};
	}

	private boolean status(CommandSender sender) {
		Player player = self(sender);
		if (player == null) {
			return true;
		}
		UUID uuid = player.getUniqueId();
		String profileId = bindings.profileFor(uuid);
		if (profileId == null) {
			sender.sendMessage(ChatColor.YELLOW + "Профиль не привязан. Попроси администратора: /lore bind " + player.getName() + " <профиль>");
			return true;
		}
		LoreProfile profile = profiles.get(profileId);
		String name = profile == null ? profileId : profile.name();
		sender.sendMessage(ChatColor.DARK_AQUA + "Профиль: " + ChatColor.WHITE + name + ChatColor.GRAY + " (" + profileId + ")");
		long enabled = LoreSettings.FEATURES.stream().filter(feature -> settings.feature(uuid, feature)).count();
		sender.sendMessage(ChatColor.DARK_AQUA + "Фичи: " + ChatColor.WHITE + enabled + "/" + LoreSettings.FEATURES.size());
		sender.sendMessage(ChatColor.DARK_AQUA + "Здоровье: " + ChatColor.WHITE + settings.param(uuid, "fragile_body.max_health"));
		sender.sendMessage(ChatColor.DARK_AQUA + "Debug: " + ChatColor.WHITE + settings.debug(uuid));
		return true;
	}

	private boolean bind(CommandSender sender, String[] args) {
		if (!admin(sender)) {
			return true;
		}
		if (args.length < 3) {
			sender.sendMessage(ChatColor.RED + "Использование: /lore bind <игрок> <профиль>");
			return true;
		}
		UUID uuid = resolve(args[1]);
		if (uuid == null) {
			sender.sendMessage(ChatColor.RED + "Игрок не найден. Он должен хотя бы раз зайти на сервер.");
			return true;
		}
		LoreProfile profile = profiles.get(args[2]);
		if (profile == null) {
			sender.sendMessage(ChatColor.RED + "Профиль не найден: " + args[2]);
			return true;
		}
		bindings.bind(uuid, profile.id(), args[1]);
		bindings.save();
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) {
			mechanics.refresh(online);
		}
		sender.sendMessage(ChatColor.GREEN + args[1] + " привязан к профилю " + profile.id());
		return true;
	}

	private boolean unbind(CommandSender sender, String[] args) {
		if (!admin(sender)) {
			return true;
		}
		if (args.length < 2) {
			sender.sendMessage(ChatColor.RED + "Использование: /lore unbind <игрок>");
			return true;
		}
		UUID uuid = resolve(args[1]);
		if (uuid == null || !bindings.unbind(uuid)) {
			sender.sendMessage(ChatColor.RED + "Привязка не найдена: " + args[1]);
			return true;
		}
		bindings.save();
		sender.sendMessage(ChatColor.GREEN + "Привязка " + args[1] + " удалена.");
		return true;
	}

	private boolean listBindings(CommandSender sender) {
		if (!admin(sender)) {
			return true;
		}
		if (bindings.all().isEmpty()) {
			sender.sendMessage(ChatColor.YELLOW + "Привязок нет.");
			return true;
		}
		sender.sendMessage(ChatColor.DARK_AQUA + "Привязки:");
		bindings.all().forEach((uuid, profile) -> {
			String name = bindings.nameFor(uuid);
			sender.sendMessage(ChatColor.GRAY + " - " + (name == null ? uuid.toString() : name) + " -> " + profile);
		});
		return true;
	}

	private boolean profile(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(ChatColor.RED + "Использование: /lore profile <list|create|delete|info|name|description|memory|familiar>");
			return true;
		}
		String action = args[1].toLowerCase(Locale.ROOT);
		switch (action) {
			case "list" -> {
				sender.sendMessage(ChatColor.DARK_AQUA + "Профили: " + ChatColor.WHITE + String.join(", ", profiles.ids()));
				return true;
			}
			case "info" -> {
				if (args.length < 3) {
					sender.sendMessage(ChatColor.RED + "Использование: /lore profile info <id>");
					return true;
				}
				LoreProfile profile = profiles.get(args[2]);
				if (profile == null) {
					sender.sendMessage(ChatColor.RED + "Профиль не найден: " + args[2]);
					return true;
				}
				sender.sendMessage(ChatColor.DARK_AQUA + "ID: " + ChatColor.WHITE + profile.id());
				sender.sendMessage(ChatColor.DARK_AQUA + "Имя: " + ChatColor.WHITE + profile.name());
				sender.sendMessage(ChatColor.DARK_AQUA + "Описание: " + ChatColor.WHITE + profile.description());
				sender.sendMessage(ChatColor.DARK_AQUA + "Воспоминания: " + ChatColor.WHITE + profile.memories().size());
				sender.sendMessage(ChatColor.DARK_AQUA + "Знакомые звуки: " + ChatColor.WHITE + profile.familiarSounds().size());
				return true;
			}
			case "create" -> {
				if (!admin(sender)) {
					return true;
				}
				if (args.length < 3 || !args[2].matches("[a-z0-9_-]+")) {
					sender.sendMessage(ChatColor.RED + "Использование: /lore profile create <id> (a-z, 0-9, _, -)");
					return true;
				}
				if (profiles.get(args[2]) != null) {
					sender.sendMessage(ChatColor.RED + "Профиль уже существует: " + args[2]);
					return true;
				}
				LoreProfile profile = new LoreProfile(args[2], args[2], "", List.of(), List.of());
				profiles.save(profile);
				sender.sendMessage(ChatColor.GREEN + "Профиль создан: " + profile.id());
				return true;
			}
			case "delete" -> {
				if (!admin(sender)) {
					return true;
				}
				if (args.length < 3 || !profiles.delete(args[2])) {
					sender.sendMessage(ChatColor.RED + "Нельзя удалить этот профиль.");
					return true;
				}
				sender.sendMessage(ChatColor.GREEN + "Профиль удалён: " + args[2]);
				return true;
			}
			case "name", "description" -> {
				if (!admin(sender)) {
					return true;
				}
				if (args.length < 4) {
					sender.sendMessage(ChatColor.RED + "Использование: /lore profile " + action + " <id> <текст>");
					return true;
				}
				LoreProfile profile = profiles.get(args[2]);
				if (profile == null) {
					sender.sendMessage(ChatColor.RED + "Профиль не найден: " + args[2]);
					return true;
				}
				String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
				if (action.equals("name")) {
					profile.setName(text);
				} else {
					profile.setDescription(text);
				}
				profiles.save(profile);
				sender.sendMessage(ChatColor.GREEN + "Профиль обновлён: " + profile.id());
				return true;
			}
			case "memory", "familiar" -> {
				return profileList(sender, args, action);
			}
			default -> {
				sender.sendMessage(ChatColor.RED + "Неизвестное действие профиля.");
				return true;
			}
		}
	}

	private boolean profileList(CommandSender sender, String[] args, String action) {
		if (!admin(sender)) {
			return true;
		}
		if (args.length < 3) {
			sender.sendMessage(ChatColor.RED + "Использование: /lore profile " + action + " <id> <add|remove|list>");
			return true;
		}
		LoreProfile profile = profiles.get(args[2]);
		if (profile == null) {
			sender.sendMessage(ChatColor.RED + "Профиль не найден: " + args[2]);
			return true;
		}
		List<String> list = action.equals("memory") ? profile.memories() : profile.familiarSounds();
		if (args.length == 3 || args[3].equalsIgnoreCase("list")) {
			sender.sendMessage(ChatColor.DARK_AQUA + (action.equals("memory") ? "Воспоминания" : "Звуки") + " " + profile.id() + ":");
			for (int index = 0; index < list.size(); index++) {
				sender.sendMessage(ChatColor.GRAY + " " + (index + 1) + ". " + list.get(index));
			}
			return true;
		}
		String operation = args[3].toLowerCase(Locale.ROOT);
		if (operation.equals("add")) {
			if (args.length < 5) {
				sender.sendMessage(ChatColor.RED + "Нужен текст или звук.");
				return true;
			}
			list.add(String.join(" ", Arrays.copyOfRange(args, 4, args.length)));
		} else if (operation.equals("remove")) {
			if (args.length < 5) {
				sender.sendMessage(ChatColor.RED + "Нужен номер элемента.");
				return true;
			}
			try {
				int index = Integer.parseInt(args[4]) - 1;
				if (index < 0 || index >= list.size()) {
					sender.sendMessage(ChatColor.RED + "Нет такого элемента.");
					return true;
				}
				list.remove(index);
			} catch (NumberFormatException exception) {
				sender.sendMessage(ChatColor.RED + "Номер должен быть числом.");
				return true;
			}
		} else {
			sender.sendMessage(ChatColor.RED + "Использование: add, remove или list.");
			return true;
		}
		profiles.save(profile);
		sender.sendMessage(ChatColor.GREEN + "Профиль обновлён: " + profile.id());
		return true;
	}

	private boolean memory(CommandSender sender, String[] args) {
		Player target = player(sender);
		if (args.length >= 2) {
			if (!admin(sender)) {
				return true;
			}
			target = Bukkit.getPlayerExact(args[1]);
			if (target == null) {
				sender.sendMessage(ChatColor.RED + "Игрок должен быть онлайн: " + args[1]);
				return true;
			}
		}
		if (target == null) {
			sender.sendMessage(ChatColor.RED + "Эта команда доступна только игроку.");
			return true;
		}
		LoreProfile profile = profile(target.getUniqueId());
		if (profile == null || profile.memories().isEmpty()) {
			sender.sendMessage(ChatColor.YELLOW + "У профиля нет воспоминаний.");
			return true;
		}
		String memory = profile.memories().get(ThreadLocalRandom.current().nextInt(profile.memories().size()));
		sender.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + memory);
		return true;
	}

	private boolean trigger(CommandSender sender, String[] args) {
		if (args.length < 2 || !args[1].equalsIgnoreCase("sound")) {
			sender.sendMessage(ChatColor.RED + "Использование: /lore trigger sound [игрок] [звук]");
			return true;
		}
		Player target = player(sender);
		if (args.length >= 3) {
			if (!admin(sender)) {
				return true;
			}
			target = Bukkit.getPlayerExact(args[2]);
		}
		if (target == null) {
			sender.sendMessage(ChatColor.RED + "Игрок должен быть онлайн.");
			return true;
		}
		String sound = args.length >= 4 ? args[3] : "minecraft:entity.warden.sonic_boom";
		mechanics.trigger(target, sound);
		sender.sendMessage(ChatColor.GREEN + "Звук " + sound + " для " + target.getName());
		return true;
	}

	private boolean feature(CommandSender sender, String[] args) {
		if (args.length == 2) {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			sender.sendMessage(ChatColor.DARK_AQUA + "Фичи " + player.getName() + ":");
			for (String feature : LoreSettings.FEATURES) {
				sender.sendMessage(ChatColor.GRAY + " - " + feature + ": " + (settings.feature(player.getUniqueId(), feature) ? "on" : "off"));
			}
			return true;
		}
		UUID uuid;
		String feature;
		String value;
		if (args.length == 3) {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
			feature = args[1];
			value = args[2];
		} else if (args.length == 4) {
			uuid = resolve(args[1]);
			if (uuid == null) {
				sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
				return true;
			}
			Player self = player(sender);
			if ((self == null || !self.getUniqueId().equals(uuid)) && !admin(sender)) {
				return true;
			}
			feature = args[2];
			value = args[3];
		} else {
			sender.sendMessage(ChatColor.RED + "Использование: /lore feature [игрок] <фича> <on|off>");
			return true;
		}
		if (!LoreSettings.FEATURES.contains(feature)) {
			sender.sendMessage(ChatColor.RED + "Неизвестная фича: " + feature);
			return true;
		}
		Boolean enabled = parseBoolean(value);
		if (enabled == null) {
			sender.sendMessage(ChatColor.RED + "Значение должно быть on или off.");
			return true;
		}
		settings.setFeature(uuid, feature, enabled);
		settings.save();
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) {
			mechanics.refresh(online);
		}
		sender.sendMessage(ChatColor.GREEN + feature + " = " + (enabled ? "on" : "off"));
		return true;
	}

	private boolean set(CommandSender sender, String[] args) {
		UUID uuid;
		String param;
		String rawValue;
		if (args.length == 3) {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
			param = args[1];
			rawValue = args[2];
		} else if (args.length == 4) {
			uuid = resolve(args[1]);
			if (uuid == null) {
				sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
				return true;
			}
			Player self = player(sender);
			if ((self == null || !self.getUniqueId().equals(uuid)) && !admin(sender)) {
				return true;
			}
			param = args[2];
			rawValue = args[3];
		} else {
			sender.sendMessage(ChatColor.RED + "Использование: /lore set [игрок] <параметр> <значение>");
			return true;
		}
		if (!LoreSettings.PARAMS.contains(param)) {
			sender.sendMessage(ChatColor.RED + "Неизвестный параметр: " + param);
			return true;
		}
		double value;
		try {
			value = Double.parseDouble(rawValue);
		} catch (NumberFormatException exception) {
			sender.sendMessage(ChatColor.RED + "Значение должно быть числом.");
			return true;
		}
		if (!Double.isFinite(value)) {
			sender.sendMessage(ChatColor.RED + "Значение должно быть конечным числом.");
			return true;
		}
		settings.setParam(uuid, param, value);
		settings.save();
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) {
			mechanics.refresh(online);
		}
		sender.sendMessage(ChatColor.GREEN + param + " = " + value);
		return true;
	}

	private boolean show(CommandSender sender, String[] args) {
		UUID uuid;
		String displayName;
		if (args.length >= 2) {
			if (!admin(sender)) {
				return true;
			}
			uuid = resolve(args[1]);
			displayName = args[1];
		} else {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
			displayName = player.getName();
		}
		if (uuid == null) {
			sender.sendMessage(ChatColor.RED + "Игрок не найден.");
			return true;
		}
		String profileId = bindings.profileFor(uuid);
		LoreProfile profile = profileId == null ? null : profiles.get(profileId);
		sender.sendMessage(ChatColor.DARK_AQUA + "Игрок: " + ChatColor.WHITE + displayName);
		sender.sendMessage(ChatColor.DARK_AQUA + "Профиль: " + ChatColor.WHITE + (profile == null ? "нет" : profile.id() + " / " + profile.name()));
		for (String feature : LoreSettings.FEATURES) {
			sender.sendMessage(ChatColor.GRAY + " - " + feature + ": " + (settings.feature(uuid, feature) ? "on" : "off"));
		}
		for (String param : LoreSettings.PARAMS) {
			sender.sendMessage(ChatColor.GRAY + " - " + param + ": " + settings.param(uuid, param));
		}
		return true;
	}

	private boolean reload(CommandSender sender) {
		if (!admin(sender)) {
			return true;
		}
		settings.load();
		profiles.load();
		bindings.load();
		for (Player player : Bukkit.getOnlinePlayers()) {
			mechanics.refresh(player);
		}
		sender.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена.");
		return true;
	}

	private boolean reset(CommandSender sender, String[] args) {
		UUID uuid;
		if (args.length >= 2) {
			if (!admin(sender)) {
				return true;
			}
			uuid = resolve(args[1]);
			if (uuid == null) {
				sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
				return true;
			}
		} else {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
		}
		settings.reset(uuid);
		settings.save();
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) {
			mechanics.refresh(online);
		}
		sender.sendMessage(ChatColor.GREEN + "Настройки сброшены.");
		return true;
	}

	private boolean debug(CommandSender sender, String[] args) {
		UUID uuid;
		String value = null;
		if (args.length == 1) {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
			value = "toggle";
		} else if (args.length == 2) {
			Player player = self(sender);
			if (player == null) {
				return true;
			}
			uuid = player.getUniqueId();
			value = args[1];
		} else {
			if (!admin(sender)) {
				return true;
			}
			uuid = resolve(args[1]);
			if (uuid == null) {
				sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
				return true;
			}
			value = args[2];
		}
		boolean current = settings.debug(uuid);
		Boolean parsed = value.equalsIgnoreCase("toggle") ? !current : parseBoolean(value);
		if (parsed == null) {
			sender.sendMessage(ChatColor.RED + "Значение должно быть on, off или toggle.");
			return true;
		}
		settings.setDebug(uuid, parsed);
		settings.save();
		sender.sendMessage(ChatColor.GREEN + "Debug = " + parsed);
		return true;
	}

	private boolean help(CommandSender sender) {
		sender.sendMessage(ChatColor.DARK_AQUA + "Lorcopika:");
		sender.sendMessage(ChatColor.GRAY + " /lore status - профиль и состояние");
		sender.sendMessage(ChatColor.GRAY + " /lore memory [игрок] - случайное воспоминание");
		sender.sendMessage(ChatColor.GRAY + " /lore trigger sound [игрок] [звук] - знакомый звук");
		sender.sendMessage(ChatColor.GRAY + " /lore feature [игрок] <фича> <on|off> - включить фичу");
		sender.sendMessage(ChatColor.GRAY + " /lore set [игрок] <параметр> <значение> - изменить параметр");
		sender.sendMessage(ChatColor.GRAY + " /lore show [игрок] - все настройки");
		sender.sendMessage(ChatColor.GRAY + " /lore bind <игрок> <профиль> - привязать профиль (admin)");
		sender.sendMessage(ChatColor.GRAY + " /lore unbind <игрок> - убрать привязку (admin)");
		sender.sendMessage(ChatColor.GRAY + " /lore bindings - все привязки (admin)");
		sender.sendMessage(ChatColor.GRAY + " /lore profile ... - профили (admin)");
		sender.sendMessage(ChatColor.GRAY + " /lore reload - перезагрузить конфиг (admin)");
		sender.sendMessage(ChatColor.GRAY + " /lore reset [игрок] - сбросить настройки");
		sender.sendMessage(ChatColor.GRAY + " /lore debug [игрок] [on|off|toggle] - отладка");
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filter(SUBCOMMANDS, args[0]);
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		return switch (sub) {
			case "bind" -> {
				if (args.length == 2) {
					yield filter(onlineNames(), args[1]);
				}
				if (args.length == 3) {
					yield filter(profiles.ids(), args[2]);
				}
				yield List.of();
			}
			case "unbind", "memory", "show", "reset" -> args.length == 2 ? filter(onlineNames(), args[1]) : List.of();
			case "profile" -> {
				if (args.length == 2) {
					yield filter(PROFILE_COMMANDS, args[1]);
				}
				if (args.length == 3) {
					yield filter(profiles.ids(), args[2]);
				}
				if (args.length == 4 && (args[1].equalsIgnoreCase("memory") || args[1].equalsIgnoreCase("familiar"))) {
					yield filter(LIST_COMMANDS, args[3]);
				}
				yield List.of();
			}
			case "trigger" -> {
				if (args.length == 2) {
					yield filter(List.of("sound"), args[1]);
				}
				if (args.length == 3) {
					yield filter(onlineNames(), args[2]);
				}
				if (args.length == 4) {
					yield filter(SOUNDS, args[3]);
				}
				yield List.of();
			}
			case "feature" -> {
				if (args.length == 2) {
					List<String> options = new ArrayList<>(LoreSettings.FEATURES);
					options.addAll(onlineNames());
					yield filter(options, args[1]);
				}
				if (args.length == 3) {
					if (LoreSettings.FEATURES.contains(args[1])) {
						yield filter(List.of("on", "off"), args[2]);
					}
					yield filter(LoreSettings.FEATURES, args[2]);
				}
				if (args.length == 4) {
					yield filter(List.of("on", "off"), args[3]);
				}
				yield List.of();
			}
			case "set" -> {
				if (args.length == 2) {
					List<String> options = new ArrayList<>(LoreSettings.PARAMS);
					options.addAll(onlineNames());
					yield filter(options, args[1]);
				}
				if (args.length == 3) {
					if (LoreSettings.PARAMS.contains(args[1])) {
						yield List.of();
					}
					yield filter(LoreSettings.PARAMS, args[2]);
				}
				yield List.of();
			}
			case "debug" -> {
				if (args.length == 2) {
					List<String> options = new ArrayList<>(List.of("on", "off", "toggle"));
					options.addAll(onlineNames());
					yield filter(options, args[1]);
				}
				if (args.length == 3) {
					yield filter(List.of("on", "off", "toggle"), args[2]);
				}
				yield List.of();
			}
			default -> List.of();
		};
	}

	private Player player(CommandSender sender) {
		return sender instanceof Player player ? player : null;
	}

	private Player self(CommandSender sender) {
		if (sender instanceof Player player) {
			return player;
		}
		sender.sendMessage(ChatColor.RED + "Эта команда доступна только игроку.");
		return null;
	}

	private boolean admin(CommandSender sender) {
		if (!sender.hasPermission("lore.admin")) {
			sender.sendMessage(ChatColor.RED + "Нужно право lore.admin.");
			return false;
		}
		return true;
	}

	private UUID resolve(String name) {
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) {
			return online.getUniqueId();
		}
		return bindings.uuidForName(name);
	}

	private LoreProfile profile(UUID uuid) {
		String profileId = bindings.profileFor(uuid);
		return profileId == null ? null : profiles.get(profileId);
	}

	private static Boolean parseBoolean(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on", "true", "yes", "enable", "enabled" -> true;
			case "off", "false", "no", "disable", "disabled" -> false;
			default -> null;
		};
	}

	private static List<String> onlineNames() {
		return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
	}

	private static List<String> filter(Iterable<String> options, String prefix) {
		List<String> result = new ArrayList<>();
		String lower = prefix.toLowerCase(Locale.ROOT);
		for (String option : options) {
			if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
				result.add(option);
			}
		}
		Collections.sort(result);
		return result;
	}
}
