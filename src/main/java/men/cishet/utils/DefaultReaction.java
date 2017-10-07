package men.cishet.utils;

import sx.blah.discord.handle.impl.obj.ReactionEmoji;

public enum DefaultReaction {
	X("❌"),
	INTERROBANG("⁉"),
	WHITE_CHECK_MARK("✅"),
NO_ENTRY("⛔"),
	QUESTION("❓")
	;
	private final String unicode;

	DefaultReaction(String unicode) {
		this.unicode = unicode;
	}

	public ReactionEmoji getReaction() {
		return ReactionEmoji.of(unicode);
	}
}
