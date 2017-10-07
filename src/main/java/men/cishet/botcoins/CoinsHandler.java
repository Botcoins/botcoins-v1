package men.cishet.botcoins;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import men.cishet.botcoins.api.CurrencyData;
import men.cishet.utils.DefaultReaction;
import men.cishet.utils.MessageUtils;
import men.cishet.utils.Tasks;
import men.cishet.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CoinsHandler implements CommandProcessor {
	private static boolean alreadyReadied = true; // Disable the default message...

	private Set<CurrencyData> cmcCurrencies = new HashSet<>();
	private long lastCoinMarketCapUpdate = 0;
	private long lastBitPayUpdate = 0;
	private long lastCoinbaseUpdate = 0;
	private double bitPayPrice = -1;
	private double coinbasePrice = -1;

	public CoinsHandler() {
		Tasks.repeatAsync(this::reloadAllPrices, 0, 120000);
	}

	public double getBitPayPrice() {
		return bitPayPrice;
	}

	public double getCoinbasePrice() {
		return coinbasePrice;
	}

	public double getCoinMarketCapPrice() {
		return getCurrencyByCode("BTC").getUsdPrice();
	}

	public List<CurrencyData> getCurrencyByName(String currencyName) {
		String searchTerm = currencyName.toLowerCase().replaceAll("\\s", "");
		return cmcCurrencies.stream()
				.filter(coin -> coin.getName().toLowerCase().replaceAll("\\s", "").contains(searchTerm))
				.sorted(Comparator.comparingDouble(coin -> -coin.getMarketCap()))
				.collect(Collectors.toList());
	}

	public List<CurrencyData> getCurrency(String currency) {
		CurrencyData byCode = getCurrencyByCode(currency);
		return byCode != null ? Collections.singletonList(byCode) : getCurrencyByName(currency);
	}

	public CurrencyData getCurrencyByCode(String currencyCode) {
		return cmcCurrencies.stream().filter(e -> e.getSymbol().equalsIgnoreCase(currencyCode)).findAny().orElse(null);
	}

	private void reloadAllPrices() {
		reloadBitPayPrice();
		reloadCoinbasePrice();
		reloadCMCPrice();
	}

	private void reloadBitPayPrice() {
		try {
			JSONArray data = Unirest.get("https://bitpay.com/rates").asJson().getBody().getObject().getJSONArray("data");
			for (int i = 0; i < data.length(); i++) {
				JSONObject currentObj = data.getJSONObject(i);
				if (currentObj.getString("code").equalsIgnoreCase("USD")) {
					bitPayPrice = currentObj.getDouble("rate");
					lastBitPayUpdate = System.currentTimeMillis();
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private void reloadCoinbasePrice() {
		try {
			coinbasePrice = Unirest.get("https://api.coinbase.com/v2/prices/BTC-USD/buy").asJson().getBody().getObject().getJSONObject("data").getDouble("amount");
			lastCoinbaseUpdate = System.currentTimeMillis();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void reloadCMCPrice() {
		try {
			HttpResponse<JsonNode> jsonResponse = Unirest.get("https://api.coinmarketcap.com/v1/ticker").asJson();
			JSONArray body = jsonResponse.getBody().getArray();
			synchronized (cmcCurrencies) {
				cmcCurrencies.clear();
				for (int i = 0; i < body.length(); i++) {
					JSONObject currency = body.getJSONObject(i);
					cmcCurrencies.add(new CurrencyData(currency.getString("name"), currency.getString("symbol"), !currency.isNull("price_btc") ? currency.getString("price_btc") : "0", !currency.isNull("price_usd") ? currency.getString("price_usd") : "0", !currency.isNull("market_cap_usd") ? currency.getString("market_cap_usd") : "0", !currency.isNull("percent_change_24h") ? currency.getString("percent_change_24h") : "0"));
				}
				CurrencyData btc = getCurrencyByCode("BTC");
				cmcCurrencies.add(new CurrencyData("American Dollar", "USD", 1 / btc.getUsdPrice(), 1, 0, 0, false));
				cmcCurrencies.add(new CurrencyData("Milli-Bitcoin", "mBTC", 0.001, btc.getUsdPrice() / 1000, btc.getMarketCap(), btc.getPctChange24h(), false));
				cmcCurrencies.add(new CurrencyData("Satoshi", "Sat", 0.00000001, btc.getUsdPrice() / 100000000, btc.getMarketCap(), btc.getPctChange24h(), false));
			}
			lastCoinMarketCapUpdate = System.currentTimeMillis();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public long getLastCoinMarketCapUpdate() {
		return lastCoinMarketCapUpdate;
	}

	@EventSubscriber
	public void onReady(ReadyEvent e) {
		if (!alreadyReadied) {
			alreadyReadied = true;
			Tasks.repeatAsync(() -> {
				try {
					RequestBuffer.request(() -> Botcoins.getClient().changePlayingText("$" + bitPayPrice + " BitPay"));
					Thread.sleep(15000);
					RequestBuffer.request(() -> Botcoins.getClient().changePlayingText("$" + coinbasePrice + " Coinbase"));
					Thread.sleep(15000);
					RequestBuffer.request(() -> Botcoins.getClient().changePlayingText("$" + getCoinMarketCapPrice() + " CoinMarketCap"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}, 0, 15000);
			Tasks.repeatAsync(() -> {
				try (PrintWriter pw = new PrintWriter("guilds.list")) {
					Botcoins.getClient().getGuilds().stream()
							.sorted(Comparator.comparingLong(guild -> -guild.getUsers().stream().filter(IUser::isBot).count()))
							.map(guild -> String.format("Bots: %s | Users: %s | Guild Icon: %s | Name: %s", guild.getUsers().stream().filter(IUser::isBot).count(), guild.getUsers().stream().filter(u -> !u.isBot()).count(), guild.getIconURL(), guild.getName()))
							.forEach(pw::println);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
			}, 0, 3600000);
		}
	}

	@EventSubscriber
	public void onMessage(MessageReceivedEvent e) {
		MessageUtils.checkForCommand("coins", e.getMessage()).ifPresent(this::processAsync);

		String mcl = e.getMessage().getContent().toLowerCase();
		if (!mcl.matches("^\\$c([cpt])( .*)?$")) {
			return;
		}
		String symbol = mcl.replaceAll("^\\$c([cpt])( .*)?$", "$1");
		String[] args = mcl.length() > 3 ? mcl.substring(4).split("\\s+") : new String[0];
		MessageUtils.Command cmd;
		switch (symbol) {
			case "p": {
				cmd = new MessageUtils.Command("price", args, e.getMessage());
				break;
			}
			case "c": {
				cmd = new MessageUtils.Command("convert", args, e.getMessage());
				break;
			}
			case "t": {
				cmd = new MessageUtils.Command("top", args, e.getMessage());
				break;
			}
			default: {
				return;
			}
		}
		process(cmd);
	}

	@Override
	public void process(MessageUtils.Command cmd) {
		if (cmcCurrencies.size() == 0) {
			Botcoins.sendSignedError(cmd, "Data is still initializing...");
			return;
		}
		switch (cmd.getCmd()) {
			case "top": {
				AtomicInteger order = new AtomicInteger(0);
				EmbedBuilder eb = new EmbedBuilder()
						.withTitle("Top 10 Cryptocurrencies at " + Botcoins.formatDate(lastCoinMarketCapUpdate));
				AtomicInteger coinCount = new AtomicInteger(0);
				cmcCurrencies.stream()
						.filter(CurrencyData::shouldIncludeInTop)
						.sorted(Comparator.comparingDouble(coin -> -coin.getMarketCap()))
						.filter(coin -> coinCount.incrementAndGet() <= 10)
						.forEach(coin -> eb.appendField(order.incrementAndGet() + ". " + coin.getTitleStringWithPriceChangeEmote(), coin.getDetailString(), true));
				Botcoins.sendSignedMessage(cmd, eb);
				break;
			}
			case "convert": {
				if (cmd.getArgs().length < 1) {
					Botcoins.sendSignedError(cmd, "Please provide a search term.");
					return;
				}
				double amtBuffer = 1;
				String[] currencies;
				try {
					amtBuffer = Double.parseDouble(cmd.getArgs()[0]);
					currencies = Arrays.copyOfRange(cmd.getArgs(), 1, cmd.getArgs().length);
				} catch (NumberFormatException ignored) {
					currencies = cmd.getArgs();
				}
				final double amount = amtBuffer;

				if (currencies.length > 2) {
					Botcoins.sendSignedError(cmd, "Sorry, I'm not smart enough to understand what you're asking for! If the coin you are looking for has spaces in it, try removing it. ie. `Ethereum Classic` -> `EthereumClassic`.");
					return;
				} else if (currencies.length > 0) {
					List<CurrencyData> from = getCurrency(currencies[0]);
					List<CurrencyData> to = currencies.length == 1 ? Collections.singletonList(getCurrencyByCode("usd")) : getCurrency(currencies[1]);

					boolean emptyFrom = from.isEmpty();
					boolean emptyTo = to.isEmpty();
					if (emptyFrom || emptyTo) {
						Botcoins.sendSignedError(cmd, "Sorry I can't find the **" + (emptyFrom && emptyTo ? "from** and **to" : (emptyFrom ? "from" : "to")) + "** currency you specified.");
					} else {
						if (from.size() > 1 && to.size() > 1) {
							EmbedBuilder eb = new EmbedBuilder()
									.withDescription("Multiple currencies found for both **from** and **to**. Please use the specific currency code.")
									.appendField("From Currencies", from.stream().map(CurrencyData::getTitleString).collect(Collectors.joining("\n")), true)
									.appendField("To Currencies", to.stream().map(CurrencyData::getTitleString).collect(Collectors.joining("\n")), true);
							Botcoins.sendSignedMessage(cmd, eb);
						} else {
							List<String> froms = new ArrayList<>();
							List<String> toAmts = new ArrayList<>();
							List<String> tos = new ArrayList<>();
							from.forEach(fromCoin -> to.forEach(toCoin -> {
								froms.add(fromCoin.getSimpleTitleString());
								toAmts.add(Utils.formatAndRound(amount * fromCoin.getUsdPrice() / toCoin.getUsdPrice(), 8));
								tos.add(toCoin.getSimpleTitleString());
							}));
							EmbedBuilder eb = new EmbedBuilder()
									.withTitle(String.format("Exchanging %s to %s", from.stream().map(CurrencyData::getSymbol).collect(Collectors.joining(", ")), to.stream().map(CurrencyData::getSymbol).collect(Collectors.joining(", "))))
									.appendField("From", "`" + Utils.formatAndRound(amount, 8) + "` " + (from.size() == 1 ? froms.get(0) : String.join("\n`" + amount + "` ", froms)), true)
									.appendField("To", String.join("\n", Utils.joinLists(Utils.frontPadToLongest(toAmts, "| %s", ' '), tos, "`%s` %s")), true);
							Botcoins.sendSignedMessage(cmd, eb);
						}
					}
				} else {
					Botcoins.sendSignedMessage(cmd, new EmbedBuilder().withDesc("Please provide at least 1 coin name/symbol for the conversion."));
				}
				break;
			}
			case "price": {
				if (cmd.getArgs().length == 0) {
					Botcoins.sendSignedError(cmd, "Please provide a search term.");
					return;
				}
				String suppliedCoinName = String.join("", cmd.getArgs());

				List<CurrencyData> coins = getCurrency(suppliedCoinName);

				int cs = coins.size();
				if (cs > 0) {
					EmbedBuilder eb = new EmbedBuilder()
							.withTitle(cs > 1 ? "Currencies with similar names" : "Currency with identical name");

					AtomicInteger coinCount = new AtomicInteger(0);
					coins.stream()
							.sorted(Comparator.comparingDouble(coin -> -coin.getMarketCap()))
							.filter(coin -> coinCount.incrementAndGet() <= 10)
							.forEach(coin -> eb.appendField(coin.getTitleStringWithPriceChangeEmote(), coin.getDetailString(), true));
					Botcoins.sendSignedMessage(cmd, eb);
				} else {
					Botcoins.sendSignedError(cmd, "No currencies with a similar name found.");
				}
				break;
			}
			case "reload": {
				if (Botcoins.getInstance().isOp(cmd.getMessage().getAuthor())) {
					reloadCMCPrice();
					Botcoins.reactToCommand(cmd, DefaultReaction.WHITE_CHECK_MARK);
					return;
				} else {
					Botcoins.reactToCommand(cmd, DefaultReaction.NO_ENTRY);
				}
				break;
			}
			default: {
				Botcoins.getInstance().process(cmd);
				break;
			}
		}
	}

	public long getLastCoinbaseUpdate() {
		return lastCoinbaseUpdate;
	}

	public long getLastBitPayUpdate() {
		return lastBitPayUpdate;
	}

	public CurrencyData[] deriveCurrencyPair(String currencies) {
		String[] segs = currencies.split("\\s+");
		if (segs.length == 2) {
			return new CurrencyData[]{getCurrencyByCode(segs[0]), getCurrencyByCode(segs[1])};
		}
		return null;
	}
}
