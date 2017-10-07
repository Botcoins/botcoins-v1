package men.cishet.utils;

import men.cishet.botcoins.BitcoinsHandler;
import men.cishet.botcoins.Botcoins;
import org.json.JSONArray;
import org.json.JSONObject;
import sx.blah.discord.util.EmbedBuilder;

import java.util.*;

public class BTCEmbedBuilder {
	public static EmbedBuilder formatTransaction(JSONObject json) {
		double spotUSD = Botcoins.getInstance().getCoinsHandler().getBitPayPrice();
		double fees = 0;
		String conf;
		if (json.has("block_height")) {
			long confirmations = Botcoins.getInstance().getBtcHandler().getCurrentHeight() - json.getLong("block_height") + 1;
			conf = ":" + (confirmations < 6 ? "white" : "large_blue") + "_circle: " + confirmations + " Confirmation" + (confirmations == 1 ? "" : "s");
		} else {
			conf = ":red_circle: Unconfirmed";
		}
		List<String> inputs = new ArrayList<>();
		List<String> outputs = new ArrayList<>();

		Map<String, Double> walletIns = new HashMap<>();
		JSONArray inputsArray = json.getJSONArray("inputs");
		for (int i = 0; i < inputsArray.length(); i++) {
			if (inputsArray.getJSONObject(i).has("prev_out")) {
				JSONObject prevOut = inputsArray.getJSONObject(i).getJSONObject("prev_out");
				double btc = prevOut.getDouble("value") / 100000000;
				fees += btc;
				if (btc > 0) {
					String addr = prevOut.getString("addr");
					if (walletIns.containsKey(addr)) {
						btc += walletIns.get(addr);
					}
					walletIns.put(addr, btc);
				}
			} else if (i == 0) {
				inputs.add("***Newly generated coins (by mining.)***");
				break;
			}
		}

		Map<String, Map.Entry<Double, Boolean>> walletOuts = new HashMap<>();
		JSONArray outputsArray = json.getJSONArray("out");
		for (int i = 0; i < outputsArray.length(); i++) {
			JSONObject comp = outputsArray.getJSONObject(i);
			double btc = comp.getDouble("value") / 100000000;
			fees -= btc;
			if (btc > 0) {
				String addr = comp.getString("addr");
				boolean spent = false;
				if (walletOuts.containsKey(addr)) {
					Map.Entry<Double, Boolean> r = walletOuts.get(addr);
					btc += r.getKey();
					spent = r.getValue() || comp.getBoolean("spent");
				}
				walletOuts.put(addr, new AbstractMap.SimpleEntry<>(btc, spent));
			}
		}


		walletIns.entrySet().stream()
				.sorted(Comparator.comparingDouble(e -> 0 - e.getValue()))
				.map(e -> ":money_with_wings: `" + e.getKey() + "`: " + Utils.round(e.getValue(), BitcoinsHandler.BTC_SHORTHAND_PRECISION) + " BTC (" + Utils.round(e.getValue() * spotUSD, 2) + " USD)")
				.forEach(inputs::add);
		walletOuts.entrySet().stream()
				.sorted(Comparator.comparingDouble(e -> 0 - e.getValue().getKey()))
				.map(e -> ":money" + (e.getValue().getValue() ? "_with_wings" : "bag") + ": `" + e.getKey() + "`: " + Utils.round(e.getValue().getKey(), BitcoinsHandler.BTC_SHORTHAND_PRECISION) + " BTC (" + Utils.round(e.getValue().getKey() * spotUSD, 2) + " USD)")
				.forEach(outputs::add);
		return formatTransaction(
				json.getString("hash"),
				conf,
				json.getLong("size"),
				fees,
				exerpt(inputs),
				exerpt(outputs),
				json.getLong("time"),
				spotUSD
		);
	}

	public static EmbedBuilder formatTransaction(String hash, String confirmations, long size, double fees, String inputs, String outputs, long timeSecs, double spotUSD) {
		return new EmbedBuilder()
				.withAuthorIcon("https://bitcoin.org/img/icons/opengraph.png")
				.withAuthorName("BTC Transaction")
				.withTitle(hash)
				.withUrl("https://blockchain.info/tx/" + hash)
				.appendField("Confirmations", confirmations, true)
				.appendField("Size", size + " bytes", true)
				.appendField("Fees", Utils.round(fees, BitcoinsHandler.BTC_SHORTHAND_PRECISION) + " BTC (" + Utils.round(fees * spotUSD, 2) + " USD)", true)
				.appendField("Transaction Time", Botcoins.formatDate(timeSecs * 1000), true)
				.appendField("Inputs", inputs, false)
				.appendField("Outputs", outputs, false);
	}

	public static EmbedBuilder formatWallet(JSONObject json) {
		double spotUSD = Botcoins.getInstance().getCoinsHandler().getBitPayPrice();
		String addr = json.getString("address");
		double finalBalance = json.getDouble("final_balance") / 100000000;
		double totalSent = json.getDouble("total_sent") / 100000000;
		double totalReceived = json.getDouble("total_received") / 100000000;
		List<String> inputs = new ArrayList<>();
		List<String> outputs = new ArrayList<>();
		JSONArray txs = json.getJSONArray("txs");
		for (int i = 0; i < txs.length(); i++) {
			JSONObject tx = txs.getJSONObject(i);
			String txHash = tx.getString("hash");
			String txTime = Botcoins.formatDate(tx.getLong("time") * 1000);

			JSONArray txInputs = tx.getJSONArray("inputs");
			double inBTC = 0;
			for (int inputIndex = 0; inputIndex < txInputs.length(); inputIndex++) {
				JSONObject input = txInputs.getJSONObject(inputIndex);
				if (input.has("prev_out")) {
					JSONObject prevOut = input.getJSONObject("prev_out");
					if (prevOut.getString("addr").equals(addr)) {
						inBTC += prevOut.getDouble("value") / 100000000;
					}
				}
			}
			if (inBTC > 0) {
				inputs.add(Utils.round(inBTC, BitcoinsHandler.BTC_SHORTHAND_PRECISION) + " BTC (" + Utils.round(inBTC * spotUSD, 2) + " USD) at " + txTime + " >`" + txHash + "`");
			}

			JSONArray txOutputs = tx.getJSONArray("out");
			double outBTC = 0;
			for (int outputIndex = 0; outputIndex < txOutputs.length(); outputIndex++) {
				JSONObject output = txOutputs.getJSONObject(outputIndex);
				if (output.getString("addr").equals(addr)) {
					outBTC += output.getDouble("value") / 100000000;
				}
			}
			if (outBTC > 0) {
				outputs.add(Utils.round(outBTC, BitcoinsHandler.BTC_SHORTHAND_PRECISION) + " BTC (" + Utils.round(outBTC * spotUSD, 2) + " USD) at " + txTime + " >`" + txHash + "`");
			}
		}
		return formatWallet(
				addr,
				json.getString("hash160"),
				json.getLong("n_tx"),
				finalBalance,
				totalSent,
				totalReceived,
				spotUSD,
				exerpt(inputs, false),
				exerpt(outputs, false)
		);
	}

	public static EmbedBuilder formatWallet(String addr, String hash, long txCount, double finalBalance, double totalSent, double totalReceived, double spotUSD, String inputs, String outputs) {
		return new EmbedBuilder()
				.withAuthorIcon("https://bitcoin.org/img/icons/opengraph.png")
				.withAuthorName("BTC Wallet Address")
				.withTitle(addr + " at " + Botcoins.formatDate(System.currentTimeMillis()))
				.withUrl("https://blockchain.info/address/" + addr)
				.withThumbnail("https://blockchain.info/qr?data=" + addr + "&size=256")
				.appendField("Hash (160 bit)", "`" + hash + "`", false)
				.appendField("Transactions", txCount + " txs", true)
				.appendField("Balance", finalBalance + " BTC\n" +
						Utils.round(finalBalance * spotUSD, 2) + " USD", true)
				.appendField("Total sent", totalSent + " BTC\n" +
						Utils.round(totalSent * spotUSD, 2) + " USD", true)
				.appendField("Total received", totalReceived + " BTC\n" +
						Utils.round(totalReceived * spotUSD, 2) + " USD", true)
				.appendField("Received Recently", outputs.length() > 0 ? outputs : "*None found.*", false)
				.appendField("Sent Recently", inputs.length() > 0 ? inputs : "*None found.*", false);
	}

	public static EmbedBuilder formatBlock(String hash, long height, String merkleRoot, long nonce, double btcSent, double spotUSD, long transactionsCount, long blockIndex, long version, double size, long timeSecs, String foundBy) {
		String url = "https://blockchain.info/block/" + hash;
		return new EmbedBuilder()
				.withAuthorName("New BTC Block")
				.withAuthorIcon("https://bitcoin.org/img/icons/opengraph.png")
				.withAuthorUrl(url)
				.withTitle("Block " + height)
				.withUrl(url)
				.appendField("Hash", "`" + hash + "`", false)
				.appendField("Merkle Root", "`" + merkleRoot + "`", false)
				.appendField("Nonce", "`" + toHex(nonce) + "`", true)
				.appendField("Total Amount Transacted", Utils.round(btcSent, 4) + " BTC\n" +
						Utils.round(btcSent * spotUSD, 2) + " USD", true)
				.appendField("TX Count", transactionsCount + " txs", true)
				.appendField("Block Index", blockIndex + "", true)
				.appendField("Miner Version", "0x" + toHex(version) + getProposalString(version), true)
				.appendField("Block Size", (size / 1000) + " kb", true)
				.appendField("Mined by", foundBy, true)
				.appendField("Time mined", Botcoins.formatDate(timeSecs * 1000), true);
	}

	private static String getProposalString(long version) {
		long ver2lsb = version % 4;
		return ver2lsb == 2 ? "\n(SegWit)" : ver2lsb == 0 ? "\n(BU)" : "";
	}

	private static String exerpt(List<String> list) {
		return exerpt(list, true);
	}

	private static String exerpt(List<String> list, boolean addFinalStr) {
		String str = "";
		int added = 0;
		for (String s : list) {
			if (str.length() + s.length() + (addFinalStr ? 25 : 1) < 1024) {
				added++;
				str += s + "\n";
			} else {
				if (addFinalStr) {
					str += "*And **" + (list.size() - added) + "** more.*";
				}
				break;
			}
		}
		return str;
	}

	private static String to32Bits(long number) {
		return to32Bits(number, 1 + "", 0 + "");
	}

	private static String to32Bits(long number, String on, String off) {
		StringBuilder bits = new StringBuilder();
		for (int i = 31; i >= 0; i--) {
			bits.append(((number >> i) % 2) == 0 ? off : on);
		}
		return bits.toString();
	}

	private static String toHex(long number) {
		return Long.toHexString(number);
	}

}
