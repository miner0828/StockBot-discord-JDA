package io.github.miner0828;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.emoji.Emoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Permissions;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.reaction.ReactionAddEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.reaction.ReactionAddListener;

public class Bot {
	private static Map<String, String> prefixes = new Hashtable<String, String>();
	private static Map<String, Double> balances = new Hashtable<String, Double>();
	private static Map<String, Long> dailyTimestamps = new Hashtable<String, Long>();
	private static Map<String, ArrayList<String>> stockOwnerships = new Hashtable<String, ArrayList<String>>();
	private static boolean shuttingDown = false;

	public static void main(String[] args) {
		readFileToDict("prefixes.settings", prefixes, String.class);
		readFileToDict("balances.settings", balances, Double.class);
		readFileToDict("dailyTimestamps.settings", dailyTimestamps, Long.class);
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File("stockOwnerships.settings")));
			String playerID;
			int length;
			while ((playerID=br.readLine())!= null) {
				length = Integer.parseInt(br.readLine());
				stockOwnerships.put(playerID, new ArrayList<String>());
				for (int i=0; i<length;i++) {
					stockOwnerships.get(playerID).add(br.readLine());
				}
				playerID = br.readLine();
			}
			br.close();
		} catch (IOException e1) {
			System.err.println("Error while reading from settings files");
			e1.printStackTrace();
			System.exit(1);
		}
		
		DiscordApi api = new DiscordApiBuilder().setToken(Security.TOKEN).login().join();
		System.out.println("Invite URL " + api.createBotInvite(Permissions.fromBitmask(67584)));
		
		api.addMessageCreateListener(new MessageCreateListener() {
			@Override
			public void onMessageCreate(MessageCreateEvent event) {
				if (event.getMessage().getAuthor().isYourself()) return;
				
				String messageText = event.getMessageContent();
				Optional<Server> server = event.getServer();
				String prefix;
				if (server.isPresent()) prefix = prefixes.get(server.get().getIdAsString());
				else prefix = "+";
				if (prefix == null) {
					prefixes.put(event.getServer().get().getIdAsString(), "+");
					prefix = "+";
				}
				if (messageText.startsWith(prefix)) {
					switch (messageText.toLowerCase().substring(prefix.length()).split(" ")[0]) {
					case "?":
					case "helpme":
					case "help":
						event.getChannel().sendMessage("```yaml\n"
								+ "Commands:\n"
								+ "    help: This. Aliases: helpme, ?\n"
								+ "    prefix: Set bot prefix (default '+'). Aliases: setprefix\n"
								+ "    stocks: Main command for stocks. Aliases: stock, stonks\n"
								+ "        get: Get info about a stock. Aliases: info\n"
								+ "        list: Get a list of all supported US stocks and stock symbols. Aliases: listall, getall\n"
								+ "        buy: Buy stock shares (stock buy <stock symbol> <amount>). Aliases: buystock\n"
								+ "        sell: Sell stock shares (stock sell <stock symbol> <amount>). Aliases: sellstock\n"
								+ "        mystock: List all the shares you own. Aliases: showstock, stocklist"
								+ "    balance: View your own or another player's balance. Aliases: bal, money\n"
								+ "    baltop: View the ranked list of all balances. Aliases: balancetop, leaderboard, topmoney\n"
								+ "    daily: Claim your daily money of $1000 (cooldown 24h). Aliases: allowance, freemoney"
								+ "```");
						break;
					case "prefix":
					case "setprefix":
						if (event.getMessage().getAuthor().isServerAdmin() || event.getMessage().getAuthor().isBotOwner()) {
							if (messageText.split(" ").length == 1) {
								prefixes.replace(event.getServer().get().getIdAsString(), "");
								event.getChannel().sendMessage("Removed bot prefix.");
							} else {
								prefixes.replace(event.getServer().get().getIdAsString(), messageText.split(" ")[messageText.split(" ").length-1]);
								event.getChannel().sendMessage("Set prefix to \"" + messageText.split(" ")[messageText.split(" ").length-1] + "\"");
							}
						} else {
							event.getChannel().sendMessage("You must be a server admin to change prefix");
						}
						break;
					case "shutdown":
						if (event.getMessage().getAuthor().isBotOwner()) {
							if (event.getMessageContent().substring(prefix.length()).contentEquals("shutdown confirm") && Bot.shuttingDown) {
								event.getChannel().sendMessage("Shutting down **ALL** instances of bot...");
								System.exit(0);
							} else if (event.getMessageContent().substring(prefix.length()).contentEquals("shutdown cancel")) {
								event.getChannel().sendMessage("Shutdown cancelled");
								Bot.shuttingDown = false;
							} else {
								Bot.shuttingDown = true;
								event.getChannel().sendMessage("Are you sure? Type \"" + prefix + "shutdown confirm\" to confirm or \"" + prefix + "shutdown cancel\" to cancel");
							}
						}
						break;
					case "stock":
					case "stocks":
					case "stonks":
						if (messageText.toLowerCase().substring(prefix.length()).split(" ").length==1) {
							event.getChannel().sendMessage("No sub-command given! Possible subcommands: get");
						} else {
							switch (messageText.toLowerCase().substring(prefix.length()).split(" ")[1]) {
							case "get":
							case "info":
								if (messageText.toLowerCase().substring(prefix.length()).split(" ").length>2) {
									NumberFormat formatter = NumberFormat.getCurrencyInstance();
									String symbol = messageText.toUpperCase().split(" ")[messageText.split(" ").length-1];
									String quoteOutput = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/quote", symbol, event.getChannel(), "symbol");
									if (quoteOutput.contains("Symbol not supported")) {
										event.getChannel().sendMessage("Invalid stock symbol " + symbol);
										break;
									}
									String stockProfileOutput = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/stock/profile", symbol, event.getChannel(), "symbol");
									//event.getChannel().sendMessage(quoteOutput);
									//event.getChannel().sendMessage(stockProfileOutput);
									EmbedBuilder eb = new EmbedBuilder();
									eb.setTitle(jsonLookupParser(stockProfileOutput, "name", true));
									eb.setColor(Color.BLUE);
									eb.setDescription(jsonLookupParser(stockProfileOutput, "description", true) + "\n" + jsonLookupParser(stockProfileOutput, "weburl", true));
									eb.setAuthor(symbol);
									if (stockProfileOutput.contains("NASDAQ")) eb.setAuthor("NASDAQ: " + symbol);
									if (stockProfileOutput.contains("NEW YORK STOCK EXCHANGE")) eb.setAuthor("NYSE: " + symbol);
									eb.addField("Current price: " + formatter.format(Double.parseDouble(jsonLookupParser(quoteOutput, "c", false))), ""
											+ "Open:              " + formatter.format(Double.parseDouble(jsonLookupParser(quoteOutput, "o", false))) + "\n"
											+ "High:              " + formatter.format(Double.parseDouble(jsonLookupParser(quoteOutput, "h", false))) + "\n"
											+ "Low:               " + formatter.format(Double.parseDouble(jsonLookupParser(quoteOutput, "l", false))) + "\n"
											+ "Previous close:    " + formatter.format(Double.parseDouble(jsonLookupParser(quoteOutput, "pc", false))) + "\n");
									eb.setFooter("Stock API provided by Finnhub and RapidAPI");
									event.getChannel().sendMessage(eb);
									eb = null;
									formatter = null;
								} else event.getChannel().sendMessage("No stock specified!");
								break;
							case "list":
							case "getall":
							case "listall":
								if (messageText.toLowerCase().substring(prefix.length()).split(" ").length>2) {
									String exchange = messageText.toUpperCase().split(" ")[messageText.split(" ").length-1];
									String list = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/stock/symbol", exchange, event.getChannel(), "exchange");
									if (list.contains("[]")) {
										event.getChannel().sendMessage("Invalid exchange");
										return;
									}
									String[] names = jsonLookupParserAll(list, "description", false);
									String[] symbols = jsonLookupParserAll(list, "symbol", false);
									try {
										event.getMessageAuthor().asUser().get().openPrivateChannel().get().sendMessage(createEmbedList(1, "All supported " + exchange + " stocks", names, symbols)).get().addReactions("⬅️", "➡️");
									} catch (InterruptedException | ExecutionException e) {
										event.getChannel().sendMessage("An error occured while sending the DM, please try again.");
										e.printStackTrace();
									}
								} else event.getChannel().sendMessage("No exchange specified!");
								break;
							case "buy":
							case "buystock":
								if (messageText.toLowerCase().substring(prefix.length()).split(" ").length>3) {
									String symbol = messageText.toUpperCase().substring(prefix.length()).split(" ")[2];
									String amountAsString = messageText.toLowerCase().substring(prefix.length()).split(" ")[3];
									int amount;
									try {
										amount = Integer.parseInt(amountAsString);
									} catch (NumberFormatException e) {
										event.getChannel().sendMessage("Invalid amount");
										return;
									}
									String quoteOutput = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/quote", symbol, event.getChannel(), "symbol");
									String playerID = event.getMessage().getAuthor().getIdAsString();
									if (quoteOutput.contains("Symbol not supported")) {
										event.getChannel().sendMessage("Invalid stock symbol " + symbol);
										return;
									}
									double totalCost = Double.parseDouble(jsonLookupParser(quoteOutput, "c", false)) * amount;
									if (!balances.containsKey(playerID)) event.getChannel().sendMessage("Not enough money!");
									else if (balances.get(playerID) > totalCost){
										balances.replace(playerID, (balances.get(playerID)-totalCost));
										if (!stockOwnerships.containsKey(playerID)) stockOwnerships.put(playerID, new ArrayList<String>());
										for (int i=0; i<amount; i++) {
											stockOwnerships.get(playerID).add(symbol);
										}
										event.getChannel().sendMessage("Successfully bought " + amount + " shares of " + symbol + " for " + NumberFormat.getCurrencyInstance().format(totalCost));
									} else event.getChannel().sendMessage("Not enough money!");
								} else event.getChannel().sendMessage("Not enough parameters! Usage: stock buy <stock> <amount>");
								break;
							case "sell":
							case "sellstock":
								
								break;
							case "mystock":
							case "showstock":
							case "stocklist":
								
								break;
							}
						break;
						//maybe add a +notes or +remindme or something
						}
					case "balance":
					case "bal":
					case "money":
						NumberFormat formatter = NumberFormat.getCurrencyInstance();
						String playerID = "";
						if (event.getMessage().getMentionedUsers().size()>0) {
							if (event.getMessage().getMentionedUsers().get(0).isYourself()) event.getChannel().sendMessage("$∞");
							else playerID = event.getMessage().getMentionedUsers().get(0).getIdAsString();
						} else playerID = event.getMessage().getAuthor().getIdAsString();
						if (balances.containsKey(playerID)) event.getChannel().sendMessage("<@" + playerID  + ">'s balance is " + formatter.format(balances.get(playerID)));
						else if (playerID.length() > 0) event.getChannel().sendMessage("User does not have a balance.");
						formatter = null;
						break;
					case "daily":
					case "allowance":
					case "freemoney":
						String playerID2 = event.getMessage().getAuthor().getIdAsString();
						NumberFormat formatter2 = NumberFormat.getCurrencyInstance();
						if (dailyTimestamps.containsKey(playerID2) && dailyTimestamps.get(playerID2)<(System.currentTimeMillis()-86400000L)) {
							if (balances.containsKey(playerID2)) balances.replace(playerID2, (balances.get(playerID2)+1000.00));
							else balances.put(playerID2, 1000.00);
							dailyTimestamps.replace(playerID2, System.currentTimeMillis());
						} else if (!dailyTimestamps.containsKey(playerID2)) {
							if (balances.containsKey(playerID2)) balances.replace(playerID2, (balances.get(playerID2)+1000.00));
							else balances.put(playerID2, 1000.00);
							dailyTimestamps.put(playerID2, System.currentTimeMillis());
						} else {
							long msToGo = dailyTimestamps.get(playerID2) - (System.currentTimeMillis()-86400000L);
							int h = (int) ((msToGo / (1000 * 60 * 60)) % 24);
							int m = (int) ((msToGo / (1000 * 60)) % 60);
							int s = (int) ((msToGo / 1000) % 60);
							event.getChannel().sendMessage(String.format("You still have %02d hours, %02d minutes, and %02d seconds before you can claim your next daily!", h, m, s));
							return;
						}
						event.getChannel().sendMessage("Daily $1000 given! You now have " + formatter2.format(balances.get(playerID2)) + "!");
						formatter2 = null;
						break;
					}
				}
				
			}
		});
		
		api.addReactionAddListener(new ReactionAddListener() {
			@Override
			public void onReactionAdd(ReactionAddEvent event) {
				if (event.getUser().isYourself()) return;
				
				Emoji emoji = event.getEmoji();
				Message botMessage;
				try {
					botMessage = event.getChannel().getMessages(1).get().getNewestMessage().get();
				} catch (InterruptedException | ExecutionException e) {
					event.getChannel().sendMessage("Error while moving pages");
					e.printStackTrace();
					return;
				}
				
				if (botMessage.getAuthor().isYourself() && botMessage.getEmbeds().get(0).getTitle().get().contains("All supported")) {
					int page = Integer.parseInt(botMessage.getEmbeds().get(0).getAuthor().get().getName().substring(5, 7));
					String exchange = botMessage.getEmbeds().get(0).getTitle().get().split(" ")[2];
					String list = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/stock/symbol", exchange, event.getChannel(), "exchange");
					String[] names = jsonLookupParserAll(list, "description", false);
					String[] symbols = jsonLookupParserAll(list, "symbol", false);
					
					if (emoji.equalsEmoji("⬅️") && page>1) page--;
					else if (emoji.equalsEmoji("➡️") && page<(names.length/50)) page++;
					else return;
					
					botMessage.delete("Page change");
					try {
						event.getChannel().sendMessage(createEmbedList(page, "All supported " + exchange + " stocks", names, symbols)).get().addReactions("⬅️", "➡️");
					} catch (InterruptedException | ExecutionException e) {
						event.getChannel().sendMessage("Error while sending message");
						e.printStackTrace();
						return;
					}
				}
			}
		});
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				saveRegularDict("prefixes.settings", prefixes);
				saveRegularDict("balances.settings", balances);
				saveRegularDict("dailyTimestamps.settings", dailyTimestamps);
				File file = new File("stockOwnerships.settings");
				System.out.println(file.getAbsolutePath());
				try {
					FileWriter fw = new FileWriter(file);
					for (Map.Entry<String, ArrayList<String>> pair: stockOwnerships.entrySet()) {
						fw.write(pair.getKey() + "\n" + pair.getValue().size() + "\n");
						for (String s: pair.getValue()) fw.write(s + "\n");
					}
					fw.close();
				} catch (IOException e) {
					System.err.println("Could not write daily timestamps to file!!");
					e.printStackTrace();
					System.exit(3);
				}
			}
		}));

	}
	private static String getApiJson(String urlStart, String payload, TextChannel channel, String payloadType) {
		String link = null;
		if (payloadType.contentEquals("symbol")) link = urlStart + "?symbol=" + payload + "&rapidapi-key=" + Security.API_KEY;
		else if (payloadType.contentEquals("exchange")) link = urlStart + "?exchange=" + payload + "&rapidapi-key=" + Security.API_KEY;
		try {
			URL url = new URL(link);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			InputStreamReader outputReader = new InputStreamReader(connection.getInputStream());
			StringBuilder s = new StringBuilder();
			int ch = ' ';
			while (ch != -1) {
				s.append((char) ch);
				ch = outputReader.read();
			}
			return new String(s);
		} catch (IOException e) {
			channel.sendMessage("Error whilist getting stock info, please try again");
			System.err.println(channel);
			System.err.println(channel.getId());
			System.err.println(link);
			e.printStackTrace();
		}
		return "";
	}
	private static String jsonLookupParser(String json, String key, boolean useStringProtectingRegex) {
		String[] splitJson = null;
		if (useStringProtectingRegex) splitJson = json.split("(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
		else splitJson = json.substring(2, json.length()-1).split(":|,");
		//System.out.println(Arrays.toString(splitJson));
		key = "\"" + key + "\"";
		for (int i=0; i<splitJson.length;i++) {
			if (key.equals(splitJson[i]) && useStringProtectingRegex) return splitJson[i+2].replace("\"", "");
			if (key.equals(splitJson[i]) && !useStringProtectingRegex) return splitJson[i+1].replace("\"", "");
		}
		return "";
	}
	private static String[] jsonLookupParserAll(String json, String key, boolean useStringProtectingRegex) {
		String[] splitJson = null;
		if (useStringProtectingRegex) splitJson = json.split("(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
		else splitJson = json.substring(2, json.length()-1).replaceAll("\\{|\\}", "").split(":|,");
		key = "\"" + key + "\"";
		ArrayList<String> output = new ArrayList<String>();
		for (int i=0; i<splitJson.length;i++) {
			if (key.equals(splitJson[i]) && useStringProtectingRegex) output.add(splitJson[i+2].replace("\"", ""));
			if (key.equals(splitJson[i]) && !useStringProtectingRegex) output.add(splitJson[i+1].replace("\"", ""));
		}
		return (String[]) Arrays.copyOf(output.toArray(), output.size(), String[].class);
	}
	private static <T> void saveRegularDict(String fileName, Map<String, T> dict) {
		File file = new File(fileName);
		System.out.println(file.getAbsolutePath());
		try {
			FileWriter fw = new FileWriter(file);
			for (Map.Entry<String, T> pair: dict.entrySet()) fw.write(pair.getKey() + "\n" + pair.getValue() + "\n");
			fw.close();
		} catch (IOException e) {
			System.err.println("Could not write balances to file!!");
			e.printStackTrace();
			System.exit(2);
		}
	}
	private static <T> void readFileToDict(String fileName, Map<String, T> dict, Class<T> clazz) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
			String key = br.readLine(), value;
			T valueT = null;
			while (key != null) {
				value = br.readLine();
				switch (clazz.toString()) {
				case "class java.lang.String":
					valueT = clazz.cast(value);
					break;
				case "class java.lang.Integer":
					valueT = clazz.cast(Integer.parseInt(value));
					break;
				case "class java.lang.Double":
					valueT = clazz.cast(Double.parseDouble(value));
					break;
				case "class java.lang.Long":
					valueT = clazz.cast(Long.parseLong(value));
					break;
				}
				dict.put(key, valueT);
				key = br.readLine();
			}
			br.close();
		} catch (IOException e) {
			System.err.println("Error while reading from settings files");
			e.printStackTrace();
			System.exit(1);
		}
	}
	private static EmbedBuilder createEmbedList(int page, String title, String[] iterate1, String[] iterate2) {
		EmbedBuilder eb = new EmbedBuilder();
		eb.setTitle(title);
		eb.setColor(Color.BLUE);
		eb.setFooter("Stock API provided by Finnhub and RapidAPI");
		eb.setAuthor("Page " + page);
		if (page < 10) eb.setAuthor("Page 0" + page);
		StringBuilder s = new StringBuilder();
		for (int i=50*(page-1);i<50*page && i<iterate1.length;i++) {
			s.append(iterate1[i] + " | " + iterate2[i] + "\n");
		}
		eb.setDescription(new String(s));
		return eb;
	}

}
