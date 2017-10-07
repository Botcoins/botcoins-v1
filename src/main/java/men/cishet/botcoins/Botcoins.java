package men.cishet.botcoins;

import men.cishet.utils.*;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Botcoins implements CommandProcessor {
	public static final String UP_INDICATOR_EMOJI = "<:upindicator:319110070850027521>";
	public static final String DOWN_INDICATOR_EMOJI = "<:downindicator:319110070119956491>";
	public static final AtomicLong cmdsSinceStartup = new AtomicLong(0);
	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	private static IDiscordClient client = null;
	private static Botcoins instance = null;
	private final BitcoinsHandler btcHandler;
	private final CoinsHandler coinsHandler;

	public Botcoins(CoinsHandler coinsHandler, BitcoinsHandler btcHandler) {
		this.coinsHandler = coinsHandler;
		this.btcHandler = btcHandler;
		instance = this;
	}

	public static void main(String[] args) {
		dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));

		CoinsHandler coinsHandler = new CoinsHandler();
		BitcoinsHandler btcHandler = new BitcoinsHandler();
		Botcoins botcoins = new Botcoins(coinsHandler, btcHandler);
		client = new ClientBuilder()
				.withToken(Utils.readResource("/discord.token"))
				.registerListener(botcoins)
				.registerListener(coinsHandler)
				.registerListener(btcHandler)
				.setMaxMessageCacheCount(0) // Default is 256
				.setMaxReconnectAttempts(Integer.MAX_VALUE)
				.login();
		botcoins.onInit();
	}

	public static IDiscordClient getClient() {
		try {
			while (client == null) {
				Thread.sleep(50);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return client;
	}

	public static Botcoins getInstance() {
		return instance;
	}

	public static String formatDate(long time) {
		return time > 0 ? dateFormatter.format(new Date(time)) : "Never";
	}

	public static void sendSignedMessage(MessageUtils.Command cmd, EmbedBuilder eb) {
		sendSignedMessage(cmd.getMessage().getChannel(), cmd.getMessage().getAuthor(), eb);
	}

	public static void sendSignedMessage(IChannel ch, IUser author, EmbedBuilder eb) {
		sendMessage(ch, author, eb.withColor(Color.ORANGE));
	}

	public static void sendSignedError(MessageUtils.Command cmd, String err) {
		EmbedBuilder eb = new EmbedBuilder()
				.withDesc(err)
				.withColor(Color.RED);
		IMessage msg = cmd.getMessage();
		sendMessage(msg.getChannel(), msg.getAuthor(), eb);
	}

	private static void sendMessage(IChannel ch, IUser author, EmbedBuilder eb) {
		RequestBuffer.request(() -> ch.sendMessage(eb
				.withFooterText(author != null ? String.format("Requested by %s#%s", author.getName(), author.getDiscriminator()) : (ch.isPrivate() ? "Automatic updates just for you." : String.format("Automatic updates fulfilled in #%s", ch.getName())))
				.withTimestamp(System.currentTimeMillis())
				.build())
		);
	}

	public static void reactToCommand(MessageUtils.Command cmd, DefaultReaction reaction) {
		RequestBuffer.request(() -> cmd.getMessage().addReaction(reaction.getReaction()));
	}

	private void onInit() {
		Tasks.runTaskLater(() -> RequestBuffer.request(() -> client.changePlayingText("v2 is ready | discord.gg/Rcp9sEJ")), 5000);
	}

	public IEmoji getEmojiForCoin(String coinName) {
		return client.getGuildByID(296098252900794369L).getEmojiByName(coinName.toLowerCase().replaceAll("[\\s_-]", ""));
	}

	@EventSubscriber
	public void onMessage(MessageReceivedEvent e) {
		MessageUtils.checkForCommand("botcoins", e.getMessage()).ifPresent(this::processAsync);
	}

	@EventSubscriber
	public void onNewMemberJoin(UserJoinEvent e) {
		if (e.getUser().getLongID() == 345450194613043201L) {
			e.getGuild().leave();
		}
	}

	public boolean isOp(IUser author) {
		return Arrays.stream(Utils.readFile("ops-snowflakes.txt").split("[\r\n]+")).anyMatch(sf -> sf.equals(author.getStringID()));
	}

	public BitcoinsHandler getBtcHandler() {
		return btcHandler;
	}

	@Override
	public void process(MessageUtils.Command cmd) {
		switch (cmd.getCmd()) {
			case "help": {
				EmbedBuilder embedBuilder = new EmbedBuilder()
						.withAuthorIcon(client.getApplicationIconURL())
						.withAuthorName("Botcoins Help");
				for (String section : Utils.readFile("commands.md").split("[\r\n]+#")) {
					if (section.isEmpty()) {
						continue;
					}
					String[] lines = section.split("[\r\n]+");
					embedBuilder.appendField(lines[0] + " commands", String.join("\n", Arrays.copyOfRange(lines, 1, lines.length)) + "\n", false);
				}
				Botcoins.sendSignedMessage(cmd, embedBuilder);
				break;
			}
			case "about": {
				long lastWSPongTime = getBtcHandler().getLastWSPongTime();
				Botcoins.sendSignedMessage(cmd, new EmbedBuilder()
						.withDesc("Botcoins - a bitcoin and crypto-currencies bot.")
						.appendField("Invite Link", Utils.readResource("/invitelink.txt"), false)
						.appendField("Last blockchain.info ping", lastWSPongTime > 0 ? formatDate(lastWSPongTime) + " (" + (System.currentTimeMillis() - lastWSPongTime) + " ms ago)" : "Never", false)
						.appendField("Last price update (updates every 2 mins)", String.format("BitPay: %s (%.2f USD)\nCoinbase: %s (%.2f USD)\nCoinMarketCap: %s (%.2f USD)", formatDate(coinsHandler.getLastBitPayUpdate()), coinsHandler.getBitPayPrice(), formatDate(coinsHandler.getLastCoinbaseUpdate()), coinsHandler.getCoinbasePrice(), formatDate(coinsHandler.getLastCoinMarketCapUpdate()), coinsHandler.getCoinMarketCapPrice()), false)
						.appendField("Memory Usage", ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576) + " MB", true)
						.appendField("Uptime", "Since: " + formatDate(ManagementFactory.getRuntimeMXBean().getStartTime()), true)
						.appendField("Statistics", String.format("%s Servers\n%s Unique Users\n%s Commands since startup", client.getGuilds().size(), client.getGuilds().stream().flatMap(g -> g.getUsers().stream()).collect(Collectors.toSet()).size(), cmdsSinceStartup), true)
						.appendField("Support Server", "https://discord.gg/Rcp9sEJ", true));
				break;
			}
			case "purge": {
				if (isOp(cmd.getMessage().getAuthor())) {
					List<IMessage> messageList = cmd.getMessage().getChannel().getMessageHistory(100).stream()
							.filter(msg -> msg.getAuthor().equals(client.getOurUser()))
							.collect(Collectors.toList());
					if (cmd.getMessage().getChannel().getModifiedPermissions(client.getOurUser()).contains(Permissions.MANAGE_MESSAGES)) {
						if (messageList.size() > 0) {
							RequestBuffer.request(() -> cmd.getMessage().getChannel().bulkDelete(messageList));
						}
					} else {
						messageList.forEach(msg -> RequestBuffer.request(msg::delete));
					}
					cmd.getMessage().getChannel().getMessageHistory().stream()
							.flatMap(msg -> msg.getReactions().stream())
							.filter(reaction -> reaction.getUsers().contains(client.getOurUser()))
							.forEach(reaction -> RequestBuffer.request(() ->
									reaction.getUsers()
											.forEach(user -> reaction.getMessage().removeReaction(user, reaction))
							));
					Botcoins.reactToCommand(cmd, DefaultReaction.WHITE_CHECK_MARK);
				} else {
					Botcoins.reactToCommand(cmd, DefaultReaction.NO_ENTRY);
				}
				break;
			}
			case "enablespam": {
				if (isOp(cmd.getMessage().getAuthor()) || cmd.getMessage().getAuthor().getPermissionsForGuild(cmd.getMessage().getGuild()).contains(Permissions.ADMINISTRATOR)) {
					Storage.FlatRowContainer cont = Storage.getOrCreateRowContainer("spamchannels.row");
					cont.addIfAbsent(cmd.getMessage().getChannel().getStringID());
					cont.save();
					Botcoins.reactToCommand(cmd, DefaultReaction.WHITE_CHECK_MARK);
					break;
				} else {
					Botcoins.reactToCommand(cmd, DefaultReaction.NO_ENTRY);
				}
				break;
			}
			case "disablespam": {
				if (isOp(cmd.getMessage().getAuthor()) || cmd.getMessage().getAuthor().getPermissionsForGuild(cmd.getMessage().getGuild()).contains(Permissions.ADMINISTRATOR)) {
					Storage.FlatRowContainer cont = Storage.getOrCreateRowContainer("spamchannels.row");
					cont.removeIfPresent(cmd.getMessage().getChannel().getStringID());
					cont.save();
					Botcoins.reactToCommand(cmd, DefaultReaction.WHITE_CHECK_MARK);
				} else {
					Botcoins.reactToCommand(cmd, DefaultReaction.NO_ENTRY);
				}
				break;
			}
			case "reconnect": {
				Botcoins.reactToCommand(cmd, DefaultReaction.NO_ENTRY);
			}
			default: {
				Botcoins.sendSignedError(cmd, "Botcoins v1 has been completely deprecated, if you still wish to use Botcoins in your server, check out Botcoins v2.\n\nInvite Link: https://discordapp.com/oauth2/authorize?scope=bot&client_id=345450194613043201&permissions=67387456\n\nDiscord Bots Link: https://bots.discord.pw/bots/345450194613043201\n\n\n*Don't know what to do? You should probably mention an admin/server owner or get help on the support server:* https://discord.gg/Rcp9sEJ\n\n**The bot will officially be deleted on December 1st.**");
				break;
			}
		}
	}

	public CoinsHandler getCoinsHandler() {
		return coinsHandler;
	}
}
