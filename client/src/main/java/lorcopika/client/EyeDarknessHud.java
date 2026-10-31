package lorcopika.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class EyeDarknessHud implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientVisuals.feature("eye_darkness")) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int start = (int) (width * 0.55);
		int alpha = (int) Math.max(0.0, Math.min(1.0, ClientConfig.get().eyeDarknessAlpha) * 255.0);
		int strips = 32;
		for (int index = 0; index < strips; index++) {
			int x0 = start + (width - start) * index / strips;
			int x1 = start + (width - start) * (index + 1) / strips;
			int stripAlpha = alpha * (index + 1) / strips;
			graphics.fill(x0, 0, x1, height, stripAlpha << 24);
		}
	}
}
