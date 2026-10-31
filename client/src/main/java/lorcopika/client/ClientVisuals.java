package lorcopika.client;

import lorcopika.LoreState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;

public final class ClientVisuals {
	private static final Identifier INFECTED_ARM = Identifier.fromNamespaceAndPath("lorcopika", "textures/entity/infected_arm.png");
	private static HumanoidArm renderingArm;

	private ClientVisuals() {
	}

	public static void setRenderingArm(HumanoidArm arm) {
		renderingArm = arm;
	}

	public static boolean feature(String id) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return false;
		}
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			LoreState state = LoreState.peek(server);
			if (state != null) {
				ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
				return serverPlayer != null && state.feature(serverPlayer, id);
			}
		}
		ClientConfig config = ClientConfig.get();
		if (!player.getGameProfile().name().equalsIgnoreCase(config.defaultNickname)) {
			return false;
		}
		if ("eye_darkness".equals(id)) {
			return config.eyeDarkness;
		}
		return "infected_hand".equals(id) && config.infectedHand;
	}

	public static Identifier handTexture(ClientAsset.Texture original) {
		if (renderingArm != HumanoidArm.RIGHT || !feature("infected_hand")) {
			return original.texturePath();
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			return original.texturePath();
		}
		return INFECTED_ARM;
	}
}
