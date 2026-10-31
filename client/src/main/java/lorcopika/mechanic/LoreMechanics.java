package lorcopika.mechanic;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lorcopika.LoreState;
import lorcopika.profile.CharacterProfile;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class LoreMechanics {
	private static final Identifier FRAGILE_BODY = Identifier.fromNamespaceAndPath("lorcopika", "fragile_body");

	private LoreMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LoreState.get(server));
		ServerPlayerEvents.JOIN.register(player -> {
			LoreState state = LoreState.get(player.level().getServer());
			state.bindings().ensureDefault(player);
			state.runtime(player);
			applyHealth(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyHealth(newPlayer));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(LoreMechanics::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(LoreMechanics::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(LoreMechanics::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(LoreMechanics::tick);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		LoreState state = LoreState.peek(player.level().getServer());
		if (state == null || !state.isBound(player)) {
			return true;
		}
		if (state.feature(player, "warden_ignore") && source.is(DamageTypes.SONIC_BOOM)) {
			state.debug(player, "warden_ignore: sonic_boom");
			return false;
		}
		return true;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer player) || blocked || damageTaken <= 0.0F) {
			return;
		}
		LoreState state = LoreState.peek(player.level().getServer());
		if (state == null || !state.isBound(player)) {
			return;
		}
		LoreState.Runtime runtime = state.runtime(player);
		if (runtime.secondary) {
			return;
		}
		ServerLevel level = player.level();
		if (state.feature(player, "fire_weakness") && isFire(source)) {
			double multiplier = Math.max(1.0, state.param(player, "fire_weakness.damage_multiplier"));
			float extra = (float) (baseDamage * (multiplier - 1.0));
			if (extra > 0.0F) {
				runtime.secondary = true;
				try {
					player.hurtServer(level, level.damageSources().magic(), extra);
				} finally {
					runtime.secondary = false;
				}
				state.debug(player, "fire_weakness: extra_damage");
			}
			if (player.isOnFire() && !runtime.fireExtended) {
				double burn = Math.max(1.0, state.param(player, "fire_weakness.burn_multiplier"));
				player.setRemainingFireTicks((int) (player.getRemainingFireTicks() * burn));
				runtime.fireExtended = true;
				state.debug(player, "fire_weakness: longer_burn");
			}
		}
		if (state.feature(player, "infected_zone")) {
			double chance = Math.max(0.0, Math.min(1.0, state.param(player, "infected_zone.chance")));
			boolean projectileHit = source.getDirectEntity() instanceof Projectile;
			boolean meleeHit = source.getDirectEntity() instanceof LivingEntity;
			boolean eligible = meleeHit;
			if (projectileHit) {
				double ratio = Math.max(0.0, Math.min(1.0, state.param(player, "infected_zone.height_ratio")));
				eligible = source.getDirectEntity().getY() >= player.getY() + player.getBbHeight() * ratio;
			}
			if (eligible && ThreadLocalRandom.current().nextDouble() < chance) {
				double multiplier = Math.max(0.0, state.param(player, "infected_zone.secondary_multiplier"));
				float secondary = (float) (damageTaken * multiplier);
				if (secondary > 0.0F) {
					runtime.secondary = true;
					try {
						player.hurtServer(level, level.damageSources().magic(), secondary);
					} finally {
						runtime.secondary = false;
					}
					state.debug(player, "infected_zone: secondary_damage");
				}
			}
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		LoreState state = LoreState.peek(level.getServer());
		if (state == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!state.feature(player, "sculk_spread")) {
				continue;
			}
			double radius = Math.max(1.0, state.param(player, "sculk_spread.radius"));
			if (player.distanceToSqr(entity) > radius * radius) {
				continue;
			}
			int maxBlocks = (int) Math.max(1.0, state.param(player, "sculk_spread.max_blocks"));
			spreadSculk(level, entity.blockPosition(), maxBlocks, (int) radius);
			state.debug(player, "sculk_spread: death_nearby");
		}
	}

	private static void spreadSculk(ServerLevel level, BlockPos origin, int maxBlocks, int radius) {
		int placed = 0;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < maxBlocks * 10 && placed < maxBlocks; attempt++) {
			BlockPos pos = origin.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(5) - 2, random.nextInt(radius * 2 + 1) - radius);
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
				continue;
			}
			if (!level.getBlockState(pos.below()).isSolid()) {
				continue;
			}
			level.setBlock(pos, Blocks.SCULK.defaultBlockState(), 3);
			placed++;
		}
	}

	private static void tick(MinecraftServer server) {
		LoreState state = LoreState.peek(server);
		if (state == null) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!state.isBound(player)) {
				continue;
			}
			LoreState.Runtime runtime = state.runtime(player);
			if (now % 10L == 0L && state.feature(player, "warden_ignore")) {
				suppressWardens(player, state);
			}
			if (state.feature(player, "random_darkness")) {
				if (runtime.nextDarkness == 0L) {
					runtime.nextDarkness = now + interval(state, player, "random_darkness.min_interval", "random_darkness.max_interval");
				} else if (now >= runtime.nextDarkness) {
					int duration = (int) Math.max(1.0, state.param(player, "random_darkness.duration"));
					player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, true));
					runtime.nextDarkness = now + interval(state, player, "random_darkness.min_interval", "random_darkness.max_interval");
					state.debug(player, "random_darkness: applied");
				}
			}
			if (state.feature(player, "nether_sickness")) {
				handleNether(player, state, runtime, now);
			}
			if (now % 20L == 0L && state.feature(player, "sense")) {
				sense(player, state);
			}
			if (state.feature(player, "memories")) {
				if (runtime.nextMemory == 0L) {
					runtime.nextMemory = now + interval(state, player, "memories.min_interval", "memories.max_interval");
				} else if (now >= runtime.nextMemory) {
					showMemory(player, state);
					runtime.nextMemory = now + interval(state, player, "memories.min_interval", "memories.max_interval");
				}
			}
			if (state.feature(player, "fire_weakness")) {
				extendFire(player, state, runtime);
			} else {
				runtime.fireExtended = false;
			}
		}
	}

	private static void handleNether(ServerPlayer player, LoreState state, LoreState.Runtime runtime, long now) {
		if (!player.level().dimension().equals(Level.NETHER)) {
			runtime.netherTicks = 0L;
			runtime.nextNetherEffect = 0L;
			return;
		}
		runtime.netherTicks++;
		double startTicks = Math.max(0.0, state.param(player, "nether_sickness.start_seconds")) * 20.0;
		long interval = Math.max(20L, (long) state.param(player, "nether_sickness.effect_interval"));
		if (runtime.netherTicks >= startTicks && now >= runtime.nextNetherEffect) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, false, true));
			runtime.nextNetherEffect = now + interval;
			state.debug(player, "nether_sickness: applied");
		}
	}

	private static void sense(ServerPlayer player, LoreState state) {
		double radius = Math.max(1.0, state.param(player, "sense.radius"));
		AABB box = player.getBoundingBox().inflate(radius);
		List<LivingEntity> entities = player.level().getEntitiesOfClass(
			LivingEntity.class,
			box,
			entity -> entity != player && entity.isAlive() && !(entity instanceof Player) && entity.getDeltaMovement().lengthSqr() > 0.0005
		);
		boolean glow = state.param(player, "sense.mode") >= 1.0;
		for (LivingEntity entity : entities) {
			if (glow) {
				entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
			} else {
				player.level().sendParticles(
					player,
					net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
					true,
					false,
					entity.getX(),
					entity.getY() + entity.getBbHeight() * 0.8,
					entity.getZ(),
					3,
					0.2,
					0.3,
					0.2,
					0.0
				);
			}
		}
		if (!entities.isEmpty()) {
			state.debug(player, "sense: " + entities.size());
		}
	}

	private static void showMemory(ServerPlayer player, LoreState state) {
		CharacterProfile profile = state.profile(player);
		if (profile == null || profile.memories.isEmpty()) {
			return;
		}
		String memory = profile.memories.get(ThreadLocalRandom.current().nextInt(profile.memories.size()));
		player.sendSystemMessage(Component.literal(memory).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		state.debug(player, "memories: shown");
	}

	private static void extendFire(ServerPlayer player, LoreState state, LoreState.Runtime runtime) {
		if (!player.isOnFire()) {
			runtime.fireExtended = false;
			return;
		}
		if (!runtime.fireExtended && player.getRemainingFireTicks() > 0) {
			double multiplier = Math.max(1.0, state.param(player, "fire_weakness.burn_multiplier"));
			player.setRemainingFireTicks((int) (player.getRemainingFireTicks() * multiplier));
			runtime.fireExtended = true;
		}
	}

	private static void suppressWardens(ServerPlayer player, LoreState state) {
		List<Warden> wardens = player.level().getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(48.0), Warden::isAlive);
		for (Warden warden : wardens) {
			warden.clearAnger(player);
			if (warden.getTarget() == player) {
				warden.setTarget(null);
				state.debug(player, "warden_ignore: target_cleared");
			}
		}
	}

	public static void applyHealth(ServerPlayer player) {
		LoreState state = LoreState.peek(player.level().getServer());
		if (state == null) {
			return;
		}
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(FRAGILE_BODY);
		if (state.feature(player, "fragile_body")) {
			double maxHealth = Math.max(1.0, Math.min(20.0, state.param(player, "fragile_body.max_health")));
			attribute.addOrUpdateTransientModifier(new AttributeModifier(FRAGILE_BODY, maxHealth - 20.0, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	public static void refreshAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyHealth(player);
		}
	}

	private static long interval(LoreState state, ServerPlayer player, String minKey, String maxKey) {
		long min = Math.max(1L, (long) state.param(player, minKey));
		long max = Math.max(min, (long) state.param(player, maxKey));
		return min + ThreadLocalRandom.current().nextLong(max - min + 1L);
	}

	private static boolean isFire(DamageSource source) {
		return source.is(DamageTypes.IN_FIRE)
			|| source.is(DamageTypes.ON_FIRE)
			|| source.is(DamageTypes.LAVA)
			|| source.is(DamageTypes.HOT_FLOOR)
			|| source.is(DamageTypes.CAMPFIRE)
			|| source.is(DamageTypes.FIREBALL)
			|| source.is(DamageTypes.UNATTRIBUTED_FIREBALL);
	}
}
