package men.cishet.botcoins.api;

import men.cishet.botcoins.CoinsHandler;
import men.cishet.botcoins.Botcoins;
import men.cishet.utils.Utils;
import sx.blah.discord.handle.obj.IEmoji;

public class CurrencyData {
	private final String name;
	private final String symbol;
	private final double btcPrice;
	private final double usdPrice;
	private final double marketCap;
	private final double pctChange24h;
	private final boolean includeInTop;

	public CurrencyData(String name, String symbol, double btcPrice, double usdPrice, double marketCap, double pctChange24h) {
		this(name, symbol, btcPrice, usdPrice, marketCap, pctChange24h, true);
	}

	public CurrencyData(String name, String symbol, String btcPrice, String usdPrice, String marketCap, String pctChange24h) throws NumberFormatException {
		this(name, symbol, Double.parseDouble(btcPrice), Double.parseDouble(usdPrice), Double.parseDouble(marketCap), Double.parseDouble(pctChange24h));
	}

	public CurrencyData(String name, String symbol, double btcPrice, double usdPrice, double marketCap, double pctChange24h, boolean includeInTop) {
		this.name = name;
		this.symbol = symbol;
		this.btcPrice = btcPrice;
		this.usdPrice = usdPrice;
		this.marketCap = marketCap;
		this.pctChange24h = pctChange24h;
		this.includeInTop = includeInTop;
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return symbol;
	}

	public double getBtcPrice() {
		return btcPrice;
	}

	public double getEthPrice() {
		return btcPrice / Botcoins.getInstance().getCoinsHandler().getCurrencyByCode("ETH").btcPrice;
	}


	public double getUsdPrice() {
		return usdPrice;
	}


	public double getMarketCap() {
		return marketCap;
	}


	public String getDetailString() {
		CoinsHandler ch = Botcoins.getInstance().getCoinsHandler();
		double ethPrice = getEthPrice();

		return (usdPrice != 1 ? "Units in circulation: " + Utils.formatAndRound(marketCap / usdPrice, 2) + "\n" +
				"24h Price Change: " + Utils.round(pctChange24h, 2) + "%\n" +
				"Market Cap: $" + Utils.formatAndRound(marketCap, 0) + " (CoinMarketCap)\n" +
				"Price USD: $" + Utils.formatAndRound(usdPrice, 4) + " (CoinMarketCap)\n" : "") +
				(ethPrice == 1 ? "" : "Price ETH: " + Utils.formatAndRound(ethPrice, 8) + " (CoinMarketCap)\n") +
				(btcPrice == 1
						? "BitPay: $" + Utils.formatAndRound(ch.getBitPayPrice(), 2) + "\n" +
						"Coinbase: $" + Utils.formatAndRound(ch.getCoinbasePrice(), 2) + "\n"
						: "Price BTC: " + Utils.formatAndRound(btcPrice, 8) + " (CoinMarketCap)\n"
				);
	}

	public double getPctChange24h() {
		return pctChange24h;
	}

	public String getTitleStringWithPriceChangeEmote() {
		return getTitleString() + " " + getPriceChangeEmoji();
	}

	public String getTitleString() {
		return getSimpleTitleString() + " " + getEmojiString();
	}

	public String getSimpleTitleString() {
		return name + " (" + symbol + ")";
	}

	public String getPriceChangeEmoji() {
		return pctChange24h < 0 ? Botcoins.DOWN_INDICATOR_EMOJI : Botcoins.UP_INDICATOR_EMOJI;
	}

	public String getEmojiString() {
		IEmoji emoji = Botcoins.getInstance().getEmojiForCoin(name);
		return emoji != null ? "<:" + emoji.getName() + ":" + emoji.getStringID() + ">" : "";
	}

	public boolean shouldIncludeInTop() {
		return includeInTop;
	}
}
