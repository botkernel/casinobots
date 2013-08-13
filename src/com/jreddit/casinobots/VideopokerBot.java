package com.jreddit.casinobots;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.simplecards.*;
import com.jreddit.botkernel.*;

/**
 *
 * Reddit VideopokerBot
 *
 */
public class VideopokerBot extends AbstractCasinoBot 
                            implements Bot, CrawlerListener {

    //
    // Unique bot name
    //
    private static final String BOT_NAME = "VIDEOPOKER_BOT";

    //
    // Default sleep time when checking for replies
    //
    private static final int SLEEP = 10; 

    //
    // Config files for our properties 
    //
    private static final String CONFIG_FILE = 
                    "../casinobots/scratch/videopokerbot/config.properties";

    private PokerEngine _engine;

    private Date _startTime;

    //
    // Stats for this run
    //
    private int _gamesStarted   = 0;
    private int _gamesPlayed    = 0;

    /**
     *
     * Provide a default no argument constructor
     *
     */
    public VideopokerBot() { }

    /**
     *
     * Initialize our bot.
     *
     */
    public void init() {

        //
        // Use this to keep track of when we are shutting down
        //
        _shutdown = false;
        
        Properties props = new Properties();
        try {
            log("Loading videopoker bot config properties...");
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            props.load(in);
            in.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            log("ERROR init()'ing " + BOT_NAME);
        }

        initProps(props);

        _engine = new PokerEngine();

        _startTime      = new Date();

        // Connect
        try {
            _user.connect();
        } catch(IOException ioe) {
            log("ERROR conecting user for " + BOT_NAME);
        }

        //
        //  
        //
        _crawler = CasinoCrawler.getCrawler();
       

        //
        // TODO don't use test crawler.
        //
        // List<String> subReddits = new ArrayList<String>();
        // subReddits.add("BlackjackBot");
        // _crawler = new Crawler(
        //                        _user,
        //                        "TEST_CRAWLER",
        //                        subReddits,
        //                        new Submissions.ListingType[] {
        //                                Submissions.ListingType.HOT,
        //                                Submissions.ListingType.NEW },
        //                        10,
        //                        10  );


        //
        // Register ourselves with the Crawler
        //
        _crawler.addListener(this);

        //
        // Create a match criteria for the crawler to notify us
        // when we need to respond to a post.
        //
        CrawlerMatchCriteria criteria = new CrawlerMatchCriteria() {
                
                public boolean match(Thing thing) {

                    String body = null;

                    //
                    // Do not consider my own posts as criteria
                    //
                    String author = thing.getAuthor();
                    if( author != null &&
                        author.equals(_user.getUsername())) {

                        // log("Ignoring my own comment " + thing.getName());
                        return false;
                    }

                    if(thing instanceof Comment) {
                        Comment comment = (Comment)thing;
                        if(comment.getBody() != null) {
                            body = ((Comment)comment).getBody().toLowerCase();
                        }
                    }

                    if(thing instanceof Submission) {
                        Submission submission = (Submission)thing;
                        if( submission.isSelfPost() &&
                            submission.getSelftext() != null) {
                            
                            body = submission.getSelftext().toLowerCase();
                        }
                    }

                    if(body == null) {
                        // Still nothing at this point, it's not a match.
                        return false;
                    }

                    if( body.indexOf("poker") != -1 ) {

                        //
                        // We match.
                        // Check that we
                        // have not already matched, and therefore
                        // already replied, to this comment.
                        //
                        if(!PersistenceUtils.isBotReplied(
                                BOT_NAME, thing.getName())) {

                            return true; 
                        }
                    }

                    return false;
                }

                public CrawlerListener getCrawlerListener() {
                    return VideopokerBot.this;
                }
            };

        //
        // Add out match criteria to the crawler.
        //
        _crawler.addMatchCriteria(criteria);

        //
        // Register the crawler with the kernel.
        //
        BotKernel.getBotKernel().addCrawler(_crawler);

    }

    /**
     *
     * Return our unique name.
     *
     */
    public String getName() {
        return BOT_NAME;
    }

    /**
     *
     * Called when shutting down.
     *
     */
    public void shutdown() {

        //
        // Indicate to sleeping threads that we need to shut down.
        //
        _shutdown = true;
    }



    /** 
     *
     * Main game loop. Check for players responding to games,
     * and then respond back to player. 
     *
     */
    public void run() {

        Date lastActivity = new Date();

        /**
         *  Main loop
         */
        while(true) {
        
            if(_shutdown) {
                return;
            }

            //
            // Connect
            //
            try {
                _user.connect();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                log("Error cannot connect.");
                sleep(SLEEP * 2);
                continue;
            }

            try {

                //
                // Handle any existing games.
                //
                List<Message> messages = Messages.getMessages(
                                                _user, 
                                                Messages.MessageType.UNREAD );
                continueGames(messages);


                //
                // log stats
                //
                log("");
                log("Games started: " + _gamesStarted );
                log("Games played:  " + _gamesPlayed );
                log("Running since: " + DATE_FORMAT.format(_startTime) );
                log("Last activity: " + DATE_FORMAT.format(lastActivity) );

                log("Sleeping...");
                sleep(SLEEP);

            } catch(RateLimitException rle) {

                // rle.printStackTrace();
                log("Caught RateLimitException: " + rle.getMessage());

                int sleepSecs = rle.getRetryTime();

                log("Sleeping " + sleepSecs + 
                    " seconds to recover from rate limit exception...");

                sleep(sleepSecs);

            } catch(IOException ioe) {
                ioe.printStackTrace();
                // log("ERROR, EXITING!!!");
                // System.exit(1);
            }

        }

    }

    /**
     *
     * Implement CrawlerListener.
     *
     * This should be a new game request.
     *
     */
    public void handleCrawlerEvent(CrawlerEvent event) {

        if(event.getType() != CrawlerEvent.CRAWLER_MATCH) {
            return;
        }

        Thing thing = event.getSource();

        // 
        // This is a crawler hit, so we are starting a new game.
        //

        if(!commonCrawlerEventChecks(thing)) {
            // Common crawler event checks have failed. 
            // We do not want to reply to this post or submission.
            return;
        }

        String body = null;

        if(thing instanceof Comment) {
            Comment comment = (Comment)thing;
            if(comment.getBody() != null) {
                body = ((Comment)comment).getBody().toLowerCase();
                log("Starting poker game from comment with: " + body);
            }
        }

        if(thing instanceof Submission) {
            Submission submission = (Submission)thing;
            if( submission.isSelfPost() &&
                submission.getSelftext() != null) {
                            
                body = submission.getSelftext().toLowerCase();
                log("Starting poker game from submission with: " + body);
            }
        }

        if(body == null) {
            return;
        }

        //
        // Still allow for backwards compatibility and playing without 
        // betting in the casino.
        //
        int bet = 1;

        String pattern = "(video)?poker(bot)? (\\d+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(body);
        if(m.find()) {
            try {
                bet = Integer.parseInt(m.group(3));
            } catch(NumberFormatException nfe) {
            }
        }

        String author = thing.getAuthor();

        String message = "";

        log("Starting new poker game with user " + author + " bet " + bet);

        Object dbLock = PersistenceUtils.getDatabaseLock();
        synchronized(dbLock) {

            boolean sufficientFunds = false;

            int bal = PersistenceUtils.getBankBalance(author);

            if(bal >= bet) {

                //
                // Create the player's hand
                //
                Hand hand = _engine.dealHand();

                //
                // Create message for player
                //
                message = createGameOutput( hand, author, bet);
           
                sufficientFunds = true;

            } else {

                sufficientFunds = false;

                message = 
                    "    You do not have sufficient funds to bet " + bet + 
                    " credit(s).  \n" +
                    "    Try betting fewer credits or try " +
                    "\"bankerbot credits\" to be granted credits.  \n";
            }

            //
            // Reply to player.
            //
            try {

                if(sufficientFunds) {
                    PersistenceUtils.setBankBalance(author, bal-bet);
                }

                sendComment(thing, message, author);

                _gamesStarted++;

            } catch(DeletedCommentException dce) {

                log("Ignoring deleted item... " + thing);

            } catch(BannedUserException bue) {

                //
                // This shouldn't happen as we should only be
                // responding in the casino sub
                //
                String subreddit = thing.getSubreddit();
                log("Banned from " + subreddit);

            } catch(RateLimitException rle) {

                //
                // TODO big todo....
                //
                // Handle a retry here....
                
                log("Caught RateLimitException: " + rle.getMessage());

                int sleepSecs = rle.getRetryTime();

                log("Sleeping " + sleepSecs +
                    " seconds to recover from rate limit exception...");

                sleep(sleepSecs);
                return;

            } catch(IOException ioe) {

                ioe.printStackTrace(); 

                //
                // Some other error replying to comment...
                //
                log("ERROR replying to:\n" + thing);
            }
        }

        //
        // Mark player as replied to start game, so that we
        // do not reply again to this same game request.
        // Or at least we tried to reply to this. 
        // Might have failed with some error like "TOO_OLD"
        // or we were banned.
        // Anyway mark it.
        //
        PersistenceUtils.setBotReplied(BOT_NAME, thing.getName());
 
    }


    /**
     *
     * Continue in-progress games.
     *
     */
    private void continueGames(List<Message> messages) throws IOException {
        
        if(messages.size() == 0) {
            return;
        }

        log("Continuing " + messages.size() + " existing poker game(s)...");
        
        for(Message message: messages) {
            
            Date d = message.getCreatedDate();

            log("Checking post date " + d + " is later than start date " 
                + _replyAfterDate);

            if(d == null) {
                Messages.markAsRead(_user, message);
                continue;
            }
            if(d.before(_replyAfterDate)) {
                //
                // NOTE
                // This message occurred before we started running.
                // Or in other words, while we were offline, assuming
                // we went down. 
                //
                // In the event that the database was destroyed since 
                // our last start, ignore these messages in order to avoid
                // massive spamming 
                //
                // Ignore this. 
                //
                // log("Ignoring old message from " + d);
                Messages.markAsRead(_user, message);
                continue;
            }

            String parentId = message.getParentId();
            String body = message.getBody().toLowerCase().trim();

            if(message.getSubreddit() == null) {

                //
                // This is probably a PM. Could also check for t4 kind
                // thing.getKind() == KIND_MESSAGE
                // parent will not be null if it is a reply to another 
                // PM. I.e. part of a PM conversation, only initial PM has
                // no parent id.

                //
                // Enhancement: allow communication with the bot
                // over PM. E.g.
                // "addsub <subreddit>"
                //

                // Could be a banned message. E.g.
                // "you have been banned from posting to [/r/IAmA:"
                // "/r/WTF"
                // Should try to parse this and remove the subreddit from
                // our crawl??? We already handle the 
                // 403 BannedUserException so maybe this is not necessary.

                Messages.markAsRead(_user, message);
                continue;
            }

            //
            // Do not play over PMs.
            //
            if(message.getKind().equals(Thing.KIND_MESSAGE)) {
                log("Not playing over PM for\n" + message);
                Messages.markAsRead(_user, message);
                continue;
            }

            log("Checking PersistenceUtils.isBotReplied " + message.getName());

            //
            // Ensure I do not reply to messages more than once.
            //
            if(PersistenceUtils.isBotReplied(BOT_NAME, message.getName())) {
                log("Not playing already replied to message\n" + message);
                Messages.markAsRead(_user, message);
                continue;
            }

            log("Getting parent comment to obtain game state.");

            //
            // Get game state
            //
            Comment parent = Comments.getComment(_user, parentId);

            if( parent == null ) {

                //
                // Can't parse game state. Nothing to do.
                // Could maybe inform user that comment was deleted???
                //

                Messages.markAsRead(_user, message);
                continue;
            }
         
            String parentBody = parent.getBody();

            if( parentBody == null ) {

                //
                // Can't parse game state. Nothing to do.
                // Could maybe inform user that comment was deleted???
                //

                Messages.markAsRead(_user, message);
                continue;
            }

            log("Checking for finished game.");

            if( parentBody != null &&
                parentBody.indexOf("Game over.") != -1) {

                //
                // This game is ended. Why are they still replying to us?
                //
                log("Skipping already finished game for reply: " + body
                    + " (author: " + message.getAuthor() + ")" );

                Messages.markAsRead(_user, message);
                continue;
            }

            String player = null;
            int bet = -1;

            log("Looking for player info.");

            //
            // Parse player and bet.
            //
            String pattern = "Player: (\\S+) bet: (\\d+) credit";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(parentBody);
            if(m.find()) {
                player = m.group(1);
                try {
                    bet = Integer.parseInt(m.group(2));
                } catch( NumberFormatException nfe ) {
                    // Not much to do here, check for -1 as bet later.
                }
            }

            log("Found player and bet " + player + " " + bet);

            if( player == null || bet == -1 ) {
                // 
                // We must not be playing a game for credits.
                //
                log("ERROR Playing game without credit bet:\n" + parentBody);
               
                Messages.markAsRead(_user, message);
                continue;

            } else {

                //
                // We are playing a game for credits.
                // Ensure this is the correct user to reply to and that we
                // have not already played this turn.
                //
             
                if(PersistenceUtils.isBotReplied(BOT_NAME, parent.getName())) {
                    log("Already played this turn for " + message);
                    Messages.markAsRead(_user, message);
                    continue;
                }

                if(!message.getAuthor().equals(player)) {
                    //
                    // This is not a reply from the player, ignore it.
                    //
                    Messages.markAsRead(_user, message);
                    continue;
                }
            }

            PokerHand playerHand = parseHand(parent.getBody());
                                                    
            log("Playing poker game " + "(" + message.getSubreddit() + ")" );
            log("   " + playerHand);

            pattern = "((x|o){5})";
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(body);
            
            if(matcher.find()) {

                //
                // This is a valid command. Conclude the game.
                //
              
                //
                // Keep an exclude set of cards we have already dealt
                // from the deck, so we do not deal them again.
                //
                Hand exclude = new PokerHand();
                Card[] cards = playerHand.getCards();
                for(Card card: cards) {
                    exclude.add(card);
                }

                //
                // Remove the cards user wants to discard.
                //
                String command = matcher.group(1);
                for(int i = 0; i < 5; i++) {
                    if(command.getBytes()[i] == 'x') {
                        // Debug
                        log("User keeps " + cards[i]);
                    } else {
                        cards[i] = null;
                    }
                }

                //
                // Now deal any cards we have null'ed out.
                // Keeping track of all the cards we need to exclude.
                //
                for(int i = 0; i < cards.length; i++) {
                    if(cards[i] == null) {
                        cards[i] = _engine.dealCard(exclude);
                        exclude.add(cards[i]);
                    }
                }

                playerHand = new PokerHand( cards );

                String output = createGameOutput(   playerHand, 
                                                    player, 
                                                    bet     );

                if(playerHand.isWinner()) {
                    int multiplier = playerHand.getWinType();
                    output += "    ...  \n";
                    output += "    Game over. You win!  \n";
                    output += "    Payout " + 
                                    (bet*multiplier) + " credit(s)  \n";

                    Object dbLock = PersistenceUtils.getDatabaseLock();
                    synchronized(dbLock) {
                        int bal = PersistenceUtils.getBankBalance(player);
                        PersistenceUtils.setBankBalance(
                                                    player, 
                                                    bal + (bet*multiplier) );
                    }

                } else {

                    output += "    ...  \n";
                    output += "    Game over. You lose.  \n";
                }

                try {
                    
                    sendComment(message, output, player);
                    PersistenceUtils.setBotReplied( BOT_NAME, parent.getName());
                    PersistenceUtils.setBotReplied(BOT_NAME, message.getName());
                    _gamesPlayed++;
                
                } catch(DeletedCommentException dce) {

                    log("Ignoring deleted comment... " + message);

                } catch(BannedUserException bue) {
                
                    //
                    // This shouldn't happen as we should only be
                    // responding in the casino sub
                    //
                    String subreddit = message.getSubreddit();
                    log("Banned from " + subreddit);

                } 

                //
                // We are done.
                // Mark this message as read.
                //
                Messages.markAsRead(_user, message);

                //
                // Continue to next game.
                //
                continue;
 

            }

            log("    Unknown command.");

            //
            // No supported actions were detected.
            // Send the output 
            //
            String output = createGameOutput(   playerHand, 
                                                player, 
                                                bet         );
            output += "    ...  \n";
            output += "    Sorry, I don't understand. " +
                "    Send a string of five x's or o's next to eachother.  \n" +
                "    Use x to indicate hold and y to indicate discard.  \n" +
                "    E.g. xxxoo holds the first three cards.  \n";

            
            try {

                sendComment(message, output, player);
                PersistenceUtils.setBotReplied( BOT_NAME, parent.getName());
                PersistenceUtils.setBotReplied(BOT_NAME, message.getName());

            } catch(DeletedCommentException dce) {

                log("Ignoring deleted comment... " + message);

            } catch(BannedUserException bue) {
     
                //
                // This shouldn't happen as we should only be
                // responding in the casino sub
                //
                String subreddit = message.getSubreddit();
                log("Banned from " + subreddit);


            } 


            //
            // No supported actions (hit, stay) were detected.
            // Mark message as read and move on.
            //
            Messages.markAsRead(_user, message);

        }

    }

    /**
     * @param text      Game text
     */
    private PokerHand parseHand(String text) {
        String line = text.split("\\r?\\n")[0].split(": ")[1];
       
        Card[] cards = new PokerCard[5];

        String pattern = "((\\d+|[JQKA])\\S)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(line);
        for(int i = 0; i < 5; i++) {
            if(m.find()) {
                cards[i] = new PokerCard(m.group(1));
            } else {
                // This is an error
                log("Parse error on: " + text);
            }
        }

        return new PokerHand( cards );
    }

    /**
     *
     * Draw the board. This will be the text of the post 
     * we reply to the user with.
     */
    private String createGameOutput(    Hand playerHand, 
                                        String player,
                                        int bet ) {

        String ret = "";

        ret += "    Player hand: " + playerHand + "  \n";

        if( player != null && bet != -1 ) {
            ret += "  \n";
            ret += "    Player: " + player + " bet: " + bet + " credit(s)\n";
        }

        return ret;
    }

   
    /**
     * Send a comment, append the bot's signature.
     */
    private void sendComment(Thing thing, String text, String player) 
                                                    throws IOException {
        int bal = -1; 
        if(player != null) { 
            Object dbLock = PersistenceUtils.getDatabaseLock();
            synchronized(dbLock) {
                bal = PersistenceUtils.getBankBalance( player );
            }

            if(bal > 0) {
                text += "\n\n" +
                    "|Credits\n" + 
                    "|---:\n" + 
                    "|" + bal + "\n";
            } else {
                text += "\n\n" +
                    "|Credits\n" + 
                    "|---:\n" + 
                    "|0\n";
            }
        }

        text += "\n\n" +
                "----\n" +
                "Commands: (x|o)*5 | E.g. xxxxx or xxxoo or xoxox. Use x to hold, use o to discard. | " +
                "[Visit Casino](/r/RoboCasino) | " +
                "[Contact My Human](http://www.reddit.com/message/compose/?to=BlackjackPitboss)    ";

        Comments.comment(_user, thing, text);
    }



}
