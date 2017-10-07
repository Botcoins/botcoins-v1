package men.cishet.utils;

import men.cishet.botcoins.Botcoins;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.Permissions;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class MessageUtils {
	public static Optional<Command> checkForCommand(String spotterPrefix, IMessage message) {
		if (!message.getAuthor().isBot() && message.getChannel().getModifiedPermissions(Botcoins.getClient().getOurUser()).contains(Permissions.SEND_MESSAGES)) {
			String msg = message.getContent();
			if (msg.toLowerCase().startsWith(spotterPrefix.toLowerCase() + ".")) {
				String[] split = msg.substring(spotterPrefix.length() + 1).split("\\s+");
				if (split.length >= 1) {
					Botcoins.cmdsSinceStartup.incrementAndGet();
					return Optional.of(new Command(split[0], Arrays.copyOfRange(split, 1, split.length), message));
				}
			}
		}
		return Optional.empty();
	}

	public static class Command {
		private final String cmd;
		private final String[] args;
		private final IMessage message;

		public Command(String cmd, String[] args, IMessage message) {
			this.cmd = cmd.toLowerCase();
			this.args = Arrays.stream(args).filter(Objects::nonNull).filter(s -> !s.isEmpty()).toArray(String[]::new);
			this.message = message;
		}

		public String getCmd() {
			return cmd;
		}

		public String[] getArgs() {
			return args;
		}

		public IMessage getMessage() {
			return message;
		}
	}
}
