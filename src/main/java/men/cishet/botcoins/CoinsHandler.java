package men.cishet.botcoins;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import men.cishet.botcoins.api.CurrencyData;
import men.cishet.utils.MessageUtils;
import men.cishet.utils.Tasks;
import org.json.JSONArray;
import org.json.JSONObject;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuffer;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
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
		// Tasks.repeatAsync(this::reloadAllPrices, 0, 120000);
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
		process(new MessageUtils.Command("shorthand: " + symbol, args, e.getMessage()));
	}

	@Override
	public void process(MessageUtils.Command cmd) {
		Botcoins.getInstance().process(cmd);
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
