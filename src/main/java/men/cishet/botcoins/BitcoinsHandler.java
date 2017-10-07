package men.cishet.botcoins;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import men.cishet.utils.*;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

public class BitcoinsHandler implements CommandProcessor {
	public static final int BTC_SHORTHAND_PRECISION = 4;
	private WebSocketClient ws;
	private long lastWSPong = 0;
	private long currentHeight = 0;
	private boolean updateTrackedTXs = true;

	public BitcoinsHandler() {
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
		if (Botcoins.getInstance().getCoinsHandler().getLastCoinbaseUpdate() == -1) {
			Botcoins.sendSignedError(cmd, "Data is still initializing...");
			return;
		}
		int argc = cmd.getArgs().length;
		switch (cmd.getCmd()) {
			case "untracktx": {
				List<String> tracks = new ArrayList<>();
				try {
					for (String tx : cmd.getArgs()) {
						if (tx.matches("^(([0-9a-f]{64})|(\\d+))$")) {
							tracks.add(getTXIndex(tx));
						} else {
							Botcoins.reactToCommand(cmd, DefaultReaction.X);
							return;
						}
					}
				} catch (Exception e) {
					Botcoins.reactToCommand(cmd, DefaultReaction.INTERROBANG);
					return;
				}
				if (tracks.size() >= 1) {
					Storage.FlatRowContainer txTrack = Storage.getOrCreateRowContainer("txTrack.row");
					List<String> rows = txTrack.getRows();
					List<String> remove = new ArrayList<>();
					tracks.forEach(txindex -> remove.addAll(rows.stream().filter(row -> row.startsWith(cmd.getMessage().getAuthor().getStringID() + "/" + txindex)).collect(Collectors.toList())));
					rows.removeAll(remove);
					txTrack.save();
					Botcoins.reactToCommand(cmd, DefaultReaction.WHITE_CHECK_MARK);
				} else {
					Botcoins.sendSignedError(cmd, "Please specify TXID(s).");
				}
				break;
			}
			case "tracktx": {
				/*
				List<String> tracks = new ArrayList<>();
				try {
					for (String tx : cmd.getArgs()) {
						if (tx.matches("^(([0-9a-f]{64})|(\\d+))$")) {
							tracks.add(getTXIndex(tx));
						} else {
							Botcoins.reactToCommand(cmd, ":x:");
							return;
						}
					}
				} catch (Exception e) {
					Botcoins.reactToCommand(cmd, ":interrobang:");
					return;
				}
				if (tracks.size() >= 1) {
					Storage.FlatRowContainer txTrack = Storage.getOrCreateRowContainer("txTrack.row");
					tracks.forEach(txindex -> txTrack.addIfAbsent(cmd.getMessage().getAuthor().getStringID() + "/" + txindex));
					txTrack.save();
					Botcoins.reactToCommand(cmd, ":white_check_mark:");
				} else {
					Botcoins.sendSignedError(cmd, "Please specify TXID(s).");
				}
				break;
				*/
				Botcoins.sendSignedMessage(cmd, new EmbedBuilder().withTitle("Feature Disabled").withDesc("This feature is currently disabled due to API ToS change. The new ToS requires me to encrypt end user data. Since this version of the bot is going to be replaced, this will be disabled until the new bot rolls out.").withColor(Color.RED));
				break;
			}
			case "tx":
			case "transaction": {
				if (argc >= 1) {
					for (String tx : cmd.getArgs()) {
						if (tx.matches("^(([0-9a-f]{64})|(\\d+))$")) {
							try {
								EmbedBuilder eb = BTCEmbedBuilder.formatTransaction(Unirest.get("https://blockchain.info/rawtx/" + tx)
										.asJson()
										.getBody()
										.getObject());
								Botcoins.sendSignedMessage(cmd, eb);
							} catch (UnirestException e) {
								Botcoins.sendSignedError(cmd, "Transaction by that id was not found.");
							} catch (JSONException e) {
								e.printStackTrace();
								Botcoins.sendSignedError(cmd, "Failed to parse the transaction data, please try again later.");
							}
						} else {
							Botcoins.sendSignedError(cmd, String.format("Invalid TXID `%s`.", tx.replaceAll("[^0-9a-fA-F]", "")));
						}
					}
				} else {
					Botcoins.sendSignedError(cmd, "Please provide TXID (the hex hash) or the tx index (number.)");
				}
				break;
			}
			case "block": {
				String blockId;
				if (argc == 1) {
					if (cmd.getArgs()[0].matches("^(([0-9a-f]{64})|(\\d+))$")) {
						blockId = cmd.getArgs()[0];
					} else {
						Botcoins.reactToCommand(cmd, DefaultReaction.X);
						break;
					}
				} else if (argc > 1) {
					Botcoins.sendSignedError(cmd, "You can only view 1 block at a time, choosing the first mentioned id.");
					break;
				} else {
					try {
						blockId = Unirest.get("https://blockchain.info/latestblock")
								.asJson()
								.getBody()
								.getObject()
								.getLong("block_index") + "";
					} catch (UnirestException e) {
						Botcoins.reactToCommand(cmd, DefaultReaction.X);
						break;
					}
				}
				try {
					JSONObject json = Unirest.get("https://blockchain.info/rawblock/" + blockId)
							.asJson()
							.getBody()
							.getObject();
					long height = json.getLong("height");
					long time = json.getLong("received_time") * 1000;

					String hash = json.getString("hash");
					double satoshisSent = 0;
					JSONArray txs = json.getJSONArray("tx");
					for (int i = 0; i < txs.length(); i++) {
						JSONObject tx = txs.getJSONObject(i);
						JSONArray txOut = tx.getJSONArray("out");
						for (int i1 = 0; i1 < txOut.length(); i1++) {
							satoshisSent += txOut.getJSONObject(i1).getLong("value");
						}
					}

					EmbedBuilder eb = BTCEmbedBuilder.formatBlock(
							hash,
							height,
							json.getString("mrkl_root"),
							json.getLong("nonce"),
							satoshisSent / 100000000,
							Botcoins.getInstance().getCoinsHandler().getBitPayPrice(),
							json.getLong("n_tx"),
							json.getLong("block_index"),
							json.getLong("ver"),
							json.getDouble("size"),
							time / 1000,
							"Unknown"
					);
					Botcoins.sendSignedMessage(cmd, eb);
				} catch (UnirestException e) {
					Botcoins.sendSignedError(cmd, "Block by that id was not found.");
				} catch (JSONException e) {
					e.printStackTrace();
					Botcoins.sendSignedError(cmd, "Failed to parse the transaction data, please try again later.");
				}
				break;
			}
			case "wallet":
			case "addr":
			case "address": {
				if (argc == 1) {
					String addr = cmd.getArgs()[0];
					if (addr.matches("^([13][a-km-zA-HJ-NP-Z1-9]{25,34})$")) {
						try {
							EmbedBuilder eb = BTCEmbedBuilder.formatWallet(Unirest.get("https://blockchain.info/rawaddr/" + addr)
									.asJson()
									.getBody()
									.getObject());
							Botcoins.sendSignedMessage(cmd, eb);
						} catch (UnirestException e) {
							Botcoins.sendSignedError(cmd, "Wallet by that address was not found.");
						} catch (JSONException e) {
							e.printStackTrace();
							Botcoins.sendSignedError(cmd, "Failed to parse the wallet data, please try again later.");
						}
					} else {
						Botcoins.reactToCommand(cmd, DefaultReaction.X);
					}
				} else if (argc > 1) {
					Botcoins.sendSignedError(cmd, "You can only view 1 wallet at a time, choosing the first mentioned id.");
				} else {
					Botcoins.sendSignedError(cmd, "Please provide a wallet address.");
				}
				break;
			}
			default: {
				Botcoins.getInstance().process(cmd);
				break;
			}
		}
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
