package lorcopika;

import lorcopika.command.LoreCommand;
import lorcopika.mechanic.LoreMechanics;

public final class LorcopikaCommon {
	private LorcopikaCommon() {
	}

	public static void init() {
		LoreMechanics.init();
		LoreCommand.register();
	}
}
