package men.cishet.botcoins;

import com.mashape.unirest.http.Unirest;
import men.cishet.utils.BTCEmbedBuilder;
import men.cishet.utils.MessageUtils;
import men.cishet.utils.Storage;
import men.cishet.utils.Tasks;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.json.JSONObject;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.EmbedBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

public class BitcoinsHandler implements CommandProcessor {
	public static final int BTC_SHORTHAND_PRECISION = 4;
	private WebSocketClient ws;
	private long lastWSPong = 0;
	private long currentHeight = 0;
	private boolean updateTrackedTXs = true;

	public BitcoinsHandler() {
		/*
		Tasks.repeatAsync(() -> {
			if (lastWSPong < System.currentTimeMillis() - 60000) {
				restartBlockchainWS();
			}
		}, 0, 30000);
		Tasks.repeatAsync(() -> {
			if (currentHeight == 0) {
				return;
			}
			if (updateTrackedTXs) {
				Storage.FlatRowContainer txTrack = Storage.getOrCreateRowContainer("txTrack.row");
				List<AbstractMap.SimpleEntry<Long, Long>> ents = txTrack.getRows().stream()
						.map(row -> {
							String[] split = row.split("/");
							return new AbstractMap.SimpleEntry<>(
									Long.parseLong(split[0]),
									Long.parseLong(split[1])
							);
						}).collect(Collectors.toList());
				ents.stream()
						.map(e -> {
							try {
								JSONObject txObj = Unirest.get("https://blockchain.info/rawtx/" + e.getValue())
										.asJson()
										.getBody()
										.getObject();
								if (txObj.has("block_height")) {
									EmbedBuilder eb = BTCEmbedBuilder.formatTransaction(txObj);
									Botcoins.sendSignedMessage(Botcoins.getClient().getUserByID(e.getKey()).getOrCreatePMChannel(), null, eb);
									if (currentHeight - txObj.getLong("block_height") + 1 >= 6) {
										return e.getKey() + "/" + e.getValue();
									}
								}
							} catch (UnirestException ex) {
								txTrack.removeIfPresent(e.getKey() + "/" + e.getValue());
							}
							return null;
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toSet())
						.forEach(txTrack::removeIfPresent);
				txTrack.save();
			}
			updateTrackedTXs = false;
		}, 30000, 60000);
		*/
	}

	@EventSubscriber
	public void onMessage(MessageReceivedEvent e) {
		MessageUtils.checkForCommand("btc", e.getMessage()).ifPresent(this::processAsync);
	}

	public void restartBlockchainWS() {
		if (ws != null && ws.isRunning()) {
			try {
				ws.stop();
				ws.destroy();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			ws = new WebSocketClient();
			try {
				ws.start();
				ws.setConnectTimeout(20000);
				ws.connect(new WSHandler(), new URI("ws://ws.blockchain.info/inv"), new ClientUpgradeRequest());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public long getLastWSPongTime() {
		return lastWSPong;
	}

	@Override
	public void process(MessageUtils.Command cmd) {
		Botcoins.getInstance().process(cmd);
	}

	private String getTXIndex(String tx) throws Exception {
		JSONObject json = Unirest.get("https://blockchain.info/rawtx/" + tx)
				.asJson()
				.getBody()
				.getObject();
		if (json.has("block_height") && currentHeight - json.getLong("block_height") + 1 >= 6) {
			throw new Exception("Hmm this block is already confirmed...");
		}
		return json.getLong("tx_index") + "";
	}

	public long getCurrentHeight() {
		return currentHeight;
	}

	private void queueUpdateTrackedTXs() {
		updateTrackedTXs = true;
	}

	public class WSHandler implements WebSocketListener {

		private ScheduledFuture<?> pingThread;

		@Override
		public void onWebSocketClose(int i, String s) {
			ws = null;
			pingThread.cancel(true);
			restartBlockchainWS();
		}

		@Override
		public void onWebSocketConnect(Session session) {
			try {
				RemoteEndpoint remote = session.getRemote();

				remote.sendString("{\"op\":\"blocks_sub\"}");
				pingThread = Tasks.repeatAsync(() -> {
					try {
						remote.sendString("{\"op\":\"ping\"}");
					} catch (IOException e) {
						Thread.currentThread().interrupt();
						e.printStackTrace();
					}
				}, 0, 30000);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onWebSocketError(Throwable t) {
			t.printStackTrace();
		}

		@Override
		public void onWebSocketBinary(byte[] bytes, int i, int i1) {
		}

		@Override
		public void onWebSocketText(String msg) {
			// System.out.println(msg);
			lastWSPong = System.currentTimeMillis();
			JSONObject json = new JSONObject(msg);
			switch (json.getString("op")) {
				case "block": {
					JSONObject x = json.getJSONObject("x");
					long height = x.getLong("height");
					if (height > currentHeight) {
						long time = x.getLong("time");
						if (time > (System.currentTimeMillis() / 1000) - 60) {
							EmbedBuilder eb = BTCEmbedBuilder.formatBlock(
									x.getString("hash"),
									height,
									x.getString("mrklRoot"),
									x.getLong("nonce"),
									x.getDouble("totalBTCSent") / 100000000,
									Botcoins.getInstance().getCoinsHandler().getBitPayPrice(),
									x.getLong("nTx"),
									x.getLong("blockIndex"),
									x.getLong("version"),
									x.getDouble("size"),
									time,
									x.getJSONObject("foundBy").getString("description")
							);
							Storage.getOrCreateRowContainer("spamchannels.row").getRows().stream()
									.filter(row -> !row.isEmpty())
									.map(Long::parseLong)
									.map(row -> Botcoins.getClient().getChannelByID(row))
									.filter(Objects::nonNull)
									.forEach(ch -> Botcoins.sendSignedMessage(ch, null, eb));
							queueUpdateTrackedTXs();
						}
						currentHeight = height;
					}
					break;
				}
			}
		}
	}
}
