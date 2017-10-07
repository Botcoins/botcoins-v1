package men.cishet.botcoins;

import men.cishet.utils.MessageUtils;
import men.cishet.utils.Tasks;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;

@FunctionalInterface
public interface CommandProcessor {
	default void processAsync(MessageUtils.Command cmd) {
		Tasks.runAsync(() -> {
			try {
				this.process(cmd);
			} catch (Exception e) {
				cmd.getMessage().addReaction(ReactionEmoji.of("\uD83C\uDD98"));
				e.printStackTrace();
			}
		});
	}

	void process(MessageUtils.Command cmd);
}
