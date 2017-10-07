
# Botcoins
* **botcoins.purge** - *Purge all recent messages (searches up to previous 100) sent by botcoins. (Bot creator only)*
* **botcoins.reconnect** - *Reconnects to the API. (Bot creator only)*
* **botcoins.enablespam** - *Enables the new block notification in the current channel. (Server admin & bot creator only.)*
* **botcoins.disablespam** - *Stops the new block notification in the current channel. (Server admin & bot creator only.)*
* **botcoins.about** - *Gives invite link, support server link and other information.*
* **botcoins.help** - *This help page.*

# Coins
* **coins.reload** *Instantly reload coins' prices. (Bot creator only)*
* **coins.top** *Shows the current price of the top 10 (alt)coins by market cap.* - (shorthand: $ct)
* **coins.price <currencyName>** *Displays information about the given currency (name or code.)* - (shorthand: $cp)
* **coins.convert [amount default 1] <fromCurrency> [toCurrency default USD]** *Calculates the worth of <amount> of <fromCurrency> in units of [toCurrency] (name or code.)* - (shorthand: $cc)

# Bitcoins-Specific Commands
* **btc.tracktx <txid>** *Get notified when your transaction gets confirmations. (Up to 6, then it will be purged.)*
* **btc.untracktx <txid>** *Stop getting notifications for the transactions specified.*
* **btc.block [hash/index default Last Block]** *Displays information about a block. Accepts block hash or block index (not height!) as the parameter. Current block is returned if none is specified.*
* **btc.tx/transaction <txid>** *Displays information about a transaction. Requires a transaction ID as the parameter.*
* **btc.wallet/addr/address <addr>** *Displays information about a wallet. Requires a wallet address as the parameter.*
