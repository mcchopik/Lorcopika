package lorcopika;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class LoreMechanics implements Listener {
	private final JavaPlugin plugin;
	private final LoreSettings settings;
	private final LoreProfileStore profiles;
	private final LoreBindingStore bindings;
	private final NamespacedKey healthKey;
	private final Map<UUID, Runtime> runtimes = new HashMap<>();
	private final Set<UUID> secondaryGuard = new HashSet<>();

	public LoreMechanics(JavaPlugin plugin, LoreSettings settings, LoreProfileStore profiles, LoreBindingStore bindings) {
		this.plugin = plugin;
		this.settings = settings;
		this.profiles = profiles;
		this.bindings = bindings;
		this.healthKey = new NamespacedKey(plugin, "fragile_body");
	}

	public void register() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
		Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
	}

	public void refresh(Player player) {
		applyHealth(player);
	}

	public void trigger(Player player, String sound) {
		LoreProfile profile = profile(player.getUniqueId());
		if (profile == null) {
			return;
		}
		player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
		familiar(player, profile);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		bindings.remember(uuid, player.getName());
		if (bindings.profileFor(uuid) == null
			&& player.getName().equalsIgnoreCase("McChopik")
			&& profiles.get("chopik") != null) {
			bindings.bind(uuid, "chopik", player.getName());
			bindings.save();
			debug(player, "bound to chopik");
		}
		Runtime runtime = runtime(uuid);
		long now = Bukkit.getCurrentTick();
		runtime.nextDarkness = now + intervalTicks(settings.param(uuid, "random_darkness.min_interval"), settings.param(uuid, "random_darkness.max_interval"));
		runtime.nextMemory = now + intervalTicks(settings.param(uuid, "memories.min_interval"), settings.param(uuid, "memories.max_interval"));
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (player.isOnline()) {
				applyHealth(player);
			}
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		runtimes.remove(uuid);
		secondaryGuard.remove(uuid);
		bindings.save();
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (player.isOnline()) {
				applyHealth(player);
			}
		});
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		UUID uuid = player.getUniqueId();
		if (secondaryGuard.contains(uuid)) {
			return;
		}
		LoreProfile profile = profile(uuid);
		if (profile == null) {
			return;
		}
		if (settings.feature(uuid, "loud_sounds") && event.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) {
			Location location = player.getLocation();
			Bukkit.getScheduler().runTask(plugin, () -> loud(location, "minecraft:entity.warden.sonic_boom"));
		}
		if (settings.feature(uuid, "warden_ignore") && event.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) {
			event.setCancelled(true);
			debug(player, "sonic boom ignored");
			return;
		}
		if (settings.feature(uuid, "fire_weakness") && isFire(event.getCause())) {
			double multiplier = Math.max(1.0, settings.param(uuid, "fire_weakness.damage_multiplier"));
			event.setDamage(event.getDamage() * multiplier);
			debug(player, "fire damage multiplied by " + multiplier);
		}
		if (settings.feature(uuid, "infected_zone") && event instanceof EntityDamageByEntityEvent byEntity) {
			handleInfectedZone(player, byEntity);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		loud(event.getLocation(), "minecraft:entity.generic.explode");
	}

	@EventHandler
	public void onBlockExplode(BlockExplodeEvent event) {
		loud(event.getBlock().getLocation(), "minecraft:entity.generic.explode");
	}

	@EventHandler
	public void onLightning(LightningStrikeEvent event) {
		loud(event.getLightning().getLocation(), "minecraft:entity.lightning_bolt.thunder");
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND
			|| event.getMaterial() != Material.GOAT_HORN
			|| !event.getAction().isRightClick()) {
			return;
		}
		loud(event.getPlayer().getLocation(), "minecraft:item.goat_horn.sound.");
	}

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		if (event.isCancelled()) {
			return;
		}
		Location location = event.getEntity().getLocation();
		if (event.getEntity() instanceof Warden) {
			for (Player player : location.getWorld().getPlayers()) {
				LoreProfile profile = profile(player.getUniqueId());
				if (profile != null && player.getLocation().distanceSquared(location) <= 576.0) {
					familiar(player, profile);
				}
			}
		}
		sculkSpread(location);
	}

	private void tick() {
		long now = Bukkit.getCurrentTick();
		for (Player player : Bukkit.getOnlinePlayers()) {
			UUID uuid = player.getUniqueId();
			LoreProfile profile = profile(uuid);
			if (profile == null) {
				continue;
			}
			Runtime runtime = runtime(uuid);
			if (settings.feature(uuid, "warden_ignore") && now % 10 == 0) {
				suppressWardens(player);
			}
			if (settings.feature(uuid, "random_darkness") && now >= runtime.nextDarkness) {
				randomDarkness(player);
				runtime.nextDarkness = now + intervalTicks(settings.param(uuid, "random_darkness.min_interval"), settings.param(uuid, "random_darkness.max_interval"));
			}
			if (settings.feature(uuid, "nether_sickness") && player.getWorld().getEnvironment() == World.Environment.NETHER) {
				runtime.netherTicks++;
				if (runtime.netherTicks >= secondsToTicks(settings.param(uuid, "nether_sickness.start_seconds")) && now >= runtime.nextNetherEffect) {
					netherSickness(player);
					runtime.nextNetherEffect = now + Math.max(20L, ticks(settings.param(uuid, "nether_sickness.effect_interval")));
				}
			} else {
				runtime.netherTicks = 0;
			}
			if (settings.feature(uuid, "sense") && now % 20 == 0) {
				sense(player);
			}
			if (settings.feature(uuid, "memories") && now >= runtime.nextMemory) {
				memory(player, profile);
				runtime.nextMemory = now + intervalTicks(settings.param(uuid, "memories.min_interval"), settings.param(uuid, "memories.max_interval"));
			}
			if (settings.feature(uuid, "fire_weakness")) {
				extendFire(player, runtime);
			}
		}
	}

	private void applyHealth(Player player) {
		UUID uuid = player.getUniqueId();
		AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(healthKey);
		if (!settings.feature(uuid, "fragile_body")) {
			return;
		}
		double maxHealth = Math.max(1.0, Math.min(20.0, settings.param(uuid, "fragile_body.max_health")));
		double modifier = maxHealth - attribute.getBaseValue();
		attribute.addModifier(new AttributeModifier(healthKey, modifier, AttributeModifier.Operation.ADD_NUMBER));
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private void suppressWardens(Player player) {
		UUID uuid = player.getUniqueId();
		if (!settings.feature(uuid, "warden_ignore")) {
			return;
		}
		for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 48.0, 48.0, 48.0, candidate -> candidate instanceof Warden)) {
			Warden warden = (Warden) entity;
			warden.clearAnger(player);
			if (warden.getTarget() == player) {
				warden.setTarget(null);
			}
		}
	}

	private void randomDarkness(Player player) {
		UUID uuid = player.getUniqueId();
		if (!settings.feature(uuid, "random_darkness")) {
			return;
		}
		int duration = (int) Math.max(1L, ticks(settings.param(uuid, "random_darkness.duration")));
		player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, true));
		debug(player, "darkness for " + duration + " ticks");
	}

	private void netherSickness(Player player) {
		player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0, false, true));
		debug(player, "nether sickness");
	}

	private void sense(Player player) {
		UUID uuid = player.getUniqueId();
		double radius = Math.max(1.0, settings.param(uuid, "sense.radius"));
		boolean glow = settings.param(uuid, "sense.mode") >= 1.0;
		for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius, candidate -> candidate instanceof LivingEntity && !(candidate instanceof Player))) {
			LivingEntity living = (LivingEntity) entity;
			if (living.isDead() || living.getVelocity().lengthSquared() < 0.0005) {
				continue;
			}
			if (glow) {
				living.setGlowing(true);
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					if (!living.isDead()) {
						living.setGlowing(false);
					}
				}, 40L);
			} else {
				Location point = living.getLocation().add(0.0, living.getHeight() * 0.8, 0.0);
				player.spawnParticle(Particle.SCULK_SOUL, point, 3, 0.2, 0.3, 0.2, 0.0);
			}
		}
	}

	private void memory(Player player, LoreProfile profile) {
		List<String> memories = profile.memories();
		if (memories.isEmpty()) {
			return;
		}
		String memory = memories.get(ThreadLocalRandom.current().nextInt(memories.size()));
		player.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + memory);
		debug(player, "memory shown");
	}

	private void handleInfectedZone(Player player, EntityDamageByEntityEvent event) {
		UUID uuid = player.getUniqueId();
		Entity damager = event.getDamager();
		double ratio = Math.max(0.0, Math.min(1.0, settings.param(uuid, "infected_zone.height_ratio")));
		boolean eligible = damager instanceof LivingEntity;
		if (damager instanceof Projectile projectile) {
			eligible = projectile.getLocation().getY() >= player.getLocation().getY() + player.getHeight() * ratio;
		}
		if (!eligible) {
			return;
		}
		double chance = Math.max(0.0, Math.min(1.0, settings.param(uuid, "infected_zone.chance")));
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		double multiplier = Math.max(0.0, settings.param(uuid, "infected_zone.secondary_multiplier"));
		double secondary = event.getFinalDamage() * multiplier;
		if (secondary <= 0.0) {
			return;
		}
		secondaryGuard.add(uuid);
		Bukkit.getScheduler().runTask(plugin, () -> {
			try {
				if (player.isOnline() && !player.isDead()) {
					player.damage(secondary, player);
				}
			} finally {
				secondaryGuard.remove(uuid);
			}
		});
		debug(player, "infected zone secondary " + secondary);
	}

	private void extendFire(Player player, Runtime runtime) {
		int ticks = player.getFireTicks();
		if (ticks <= 0) {
			runtime.fireExtended = false;
			return;
		}
		if (runtime.fireExtended) {
			return;
		}
		double multiplier = Math.max(1.0, settings.param(player.getUniqueId(), "fire_weakness.burn_multiplier"));
		player.setFireTicks((int) (ticks * multiplier));
		runtime.fireExtended = true;
	}

	private void loud(Location location, String sound) {
		if (location.getWorld() == null) {
			return;
		}
		long now = Bukkit.getCurrentTick();
		for (Player player : location.getWorld().getPlayers()) {
			UUID uuid = player.getUniqueId();
			LoreProfile profile = profile(uuid);
			if (profile == null || !settings.feature(uuid, "loud_sounds")) {
				continue;
			}
			double radius = Math.max(1.0, settings.param(uuid, "loud_sounds.radius"));
			if (player.getLocation().distanceSquared(location) > radius * radius) {
				continue;
			}
			Runtime runtime = runtime(uuid);
			if (now < runtime.nextLoud) {
				continue;
			}
			runtime.nextLoud = now + Math.max(1L, ticks(settings.param(uuid, "loud_sounds.interval")));
			double damage = Math.max(0.0, settings.param(uuid, "loud_sounds.damage"));
			if (damage > 0.0) {
				player.damage(damage);
			}
			debug(player, "loud sound " + sound);
			if (profile.familiar(sound)) {
				familiar(player, profile);
			}
		}
	}

	private void familiar(Player player, LoreProfile profile) {
		UUID uuid = player.getUniqueId();
		if (!settings.feature(uuid, "familiar_sound")) {
			return;
		}
		Runtime runtime = runtime(uuid);
		long now = Bukkit.getCurrentTick();
		if (now < runtime.nextFamiliar) {
			return;
		}
		runtime.nextFamiliar = now + 200L;
		int duration = (int) Math.max(1L, ticks(settings.param(uuid, "familiar_sound.slow_duration")));
		int amplifier = (int) Math.max(0.0, settings.param(uuid, "familiar_sound.slow_amplifier"));
		player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, false));
		player.sendActionBar("...");
		debug(player, "familiar sound");
	}

	private void sculkSpread(Location location) {
		World world = location.getWorld();
		if (world == null) {
			return;
		}
		for (Player player : world.getPlayers()) {
			UUID uuid = player.getUniqueId();
			LoreProfile profile = profile(uuid);
			if (profile == null || !settings.feature(uuid, "sculk_spread")) {
				continue;
			}
			double radius = Math.max(1.0, settings.param(uuid, "sculk_spread.radius"));
			if (player.getLocation().distanceSquared(location) > radius * radius) {
				continue;
			}
			int maxBlocks = Math.min(64, Math.max(1, (int) settings.param(uuid, "sculk_spread.max_blocks")));
			int radiusInt = Math.max(1, (int) radius);
			int placed = 0;
			for (int attempt = 0; attempt < maxBlocks * 10 && placed < maxBlocks; attempt++) {
				int dx = ThreadLocalRandom.current().nextInt(-radiusInt, radiusInt + 1);
				int dy = ThreadLocalRandom.current().nextInt(-2, 3);
				int dz = ThreadLocalRandom.current().nextInt(-radiusInt, radiusInt + 1);
				Block block = world.getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz);
				if (block.isEmpty() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
					block.setType(Material.SCULK, false);
					placed++;
				}
			}
			debug(player, "sculk spread placed " + placed);
		}
	}

	private boolean isFire(EntityDamageEvent.DamageCause cause) {
		return cause == EntityDamageEvent.DamageCause.FIRE
			|| cause == EntityDamageEvent.DamageCause.FIRE_TICK
			|| cause == EntityDamageEvent.DamageCause.LAVA
			|| cause == EntityDamageEvent.DamageCause.HOT_FLOOR
			|| cause == EntityDamageEvent.DamageCause.CAMPFIRE;
	}

	private LoreProfile profile(UUID uuid) {
		if (bindings.profileFor(uuid) == null) {
			return null;
		}
		return profiles.get(bindings.profileFor(uuid));
	}

	private Runtime runtime(UUID uuid) {
		return runtimes.computeIfAbsent(uuid, key -> new Runtime());
	}

	private static long ticks(double value) {
		return Math.max(0L, Math.round(value));
	}

	private static int secondsToTicks(double seconds) {
		return Math.max(0, (int) Math.round(seconds * 20.0));
	}

	private static long intervalTicks(double min, double max) {
		double low = Math.max(1.0, Math.min(min, max));
		double high = Math.max(low, Math.max(min, max));
		double value = low == high ? low : ThreadLocalRandom.current().nextDouble(low, high);
		return Math.max(1L, Math.round(value));
	}

	private void debug(Player player, String message) {
		if (settings.debug(player.getUniqueId())) {
			player.sendMessage(ChatColor.DARK_GRAY + "[lore] " + message);
		}
	}

	private static final class Runtime {
		private long nextDarkness = Bukkit.getCurrentTick() + 1200L;
		private long nextMemory = Bukkit.getCurrentTick() + 2400L;
		private long nextLoud;
		private long nextFamiliar;
		private long nextNetherEffect;
		private int netherTicks;
		private boolean fireExtended;
	}
}
