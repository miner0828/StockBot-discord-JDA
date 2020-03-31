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
	private static final String TOKEN = "";
	private static final String API_KEY = "";
	private static Map<String, String> prefixes = new Hashtable<String, String>();
	private static boolean shuttingDown = false;

	public static void main(String[] args) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File("servers.settings")));
			String key = br.readLine(), value;
			while (key != null) {
				value = br.readLine();
				prefixes.put(key, value);
				key = br.readLine();
			}
			br.close();
		} catch (IOException e1) {
			System.err.println("Error while reading from settings file");
			e1.printStackTrace();
			System.exit(1);
		}
		
		DiscordApi api = new DiscordApiBuilder().setToken(Bot.TOKEN).login().join();
		System.out.println("Invite URL " + api.createBotInvite(Permissions.fromBitmask(67584)));
		
		api.addMessageCreateListener(new MessageCreateListener() {
			@Override
			public void onMessageCreate(MessageCreateEvent event) {
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
								+ "    stocks: Main command for stocks. Aliases: stock\n"
								+ "        get: Get info about a stock. Aliases: info\n"
								+ "        list: Get a list of all supported US stocks and stock symbols. Aliases: listall, getall"
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
									String[] names = jsonLookupParserAll(list, "description", false);
									String[] symbols = jsonLookupParserAll(list, "symbol", false);
									EmbedBuilder eb = new EmbedBuilder();
									eb.setTitle("All supported " + exchange + " stocks");
									eb.setColor(Color.BLUE);
									eb.setFooter("Stock API provided by Finnhub and RapidAPI");
									eb.setAuthor("Page 01");
									StringBuilder s = new StringBuilder();
									for (int i=0;i<50;i++) {
										s.append(names[i] + " | " + symbols[i] + "\n");
									}
									eb.setDescription(new String(s));
									System.out.println(new String(s));
									try {
										event.getMessageAuthor().asUser().get().openPrivateChannel().get().sendMessage(eb).get().addReactions("⬅️", "➡️");
									} catch (InterruptedException | ExecutionException e) {
										event.getChannel().sendMessage("An error occured while sending the DM, please try again.");
										e.printStackTrace();
									}
									eb = null;
								} else event.getChannel().sendMessage("No exchange specified!");
								break;
							}
						break;
						//maybe add a +notes or +remindme or something
						}
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
				
				System.out.println("contains all supported?" + event.getMessageContent().get().contains("All supported"));
				System.out.println("page num: " + botMessage.getEmbeds().get(0).getAuthor().get().getName());
				
				if (botMessage.getAuthor().isYourself() && botMessage.getEmbeds().get(0).getTitle().get().contains("All supported")) {
					int page = Integer.parseInt(botMessage.getEmbeds().get(0).getAuthor().get().getName().substring(5, 7));
					String exchange = botMessage.getEmbeds().get(0).getTitle().get().split(" ")[2];
					System.out.println("exchange: " + exchange);
					String list = getApiJson("https://finnhub-realtime-stock-price.p.rapidapi.com/stock/symbol", exchange, event.getChannel(), "exchange");
					String[] names = jsonLookupParserAll(list, "description", false);
					String[] symbols = jsonLookupParserAll(list, "symbol", false);
					
					//System.out.println("list: " + list);
					if (emoji.equalsEmoji("⬅️") && page>1) page--;
					else if (emoji.equalsEmoji("➡️") && page<(names.length/50)) page++;
					else return;
					System.out.println("page " + page);
					
					EmbedBuilder eb = new EmbedBuilder();
					eb.setTitle("All supported " + exchange + " stocks");
					eb.setColor(Color.BLUE);
					eb.setFooter("Stock API provided by Finnhub and RapidAPI");
					eb.setAuthor("Page " + page);
					if (page < 10) eb.setAuthor("Page 0" + page);
					StringBuilder s = new StringBuilder();
					for (int i=0*page;i<50*page;i++) {
						s.append(names[i] + " | " + symbols[i] + "\n");
					}
					eb.setDescription(new String(s));
					botMessage.delete("Page change");
					event.getChannel().sendMessage("EEEEE");
					event.getChannel().sendMessage(eb);
					eb = null;
				}
			}
		});
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				File file = new File("servers.settings");
				System.out.println(file.getAbsolutePath());
				try {
					FileWriter fw = new FileWriter(file);
					for (Map.Entry<String, String> pair: prefixes.entrySet()) {
						fw.write(pair.getKey() + "\n" + pair.getValue() + "\n");
					}
					fw.close();
				} catch (IOException e) {
					System.err.println("Could not write server settings to file!!");
					e.printStackTrace();
					System.exit(2);
				}
			}
		}));

	}
	private static String getApiJson(String urlStart, String payload, TextChannel channel, String payloadType) {
		String link = null;
		if (payloadType.contentEquals("symbol")) link = urlStart + "?symbol=" + payload + "&rapidapi-key=" + Bot.API_KEY;
		else if (payloadType.contentEquals("exchange")) link = urlStart + "?exchange=" + payload + "&rapidapi-key=" + Bot.API_KEY;
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

}
