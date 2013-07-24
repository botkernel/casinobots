package com.jreddit.casinobots;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.simplecards.*;
import com.jreddit.botkernel.*;

/**
 *
 * Reddit BlackjackBot
 *
 */
public class BlackjackBot extends BaseBot implements Bot, CrawlerListener {

    //
    // String representing back of a card. I.e. card face not visible.
    //
    private static final String CARD_BACK = "██";

    //
    // Unique bot name
    //
    private static final String BOT_NAME = "BLACKJACK_BOT";

    private static final String CRAWLER_NAME = "BLACKJACK_CRAWLER";

    //
    // The sleep time for the Crawler we will register
    //
    private static final int MINUTE_SLEEP = 60;

    //
    // Some sleep times we will use, based on activity.
    //
    private static final int LONG_SLEEP     = 60 * 3;
    private static final int SHORT_SLEEP    = 30;
    private static final int MICRO_SLEEP    = 10;

    //
    // Sleep transition threshold
    // How many short sleeps we will do before entering long sleep.
    // (I.e. before becoming "inactive".)
    //
    private static final int INACTIVITY_THRESHOLD = 8;

    // For logging times.
    public static DateFormat DATE_FORMAT =
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    //
    // This is out crawler, which looks for new game requests.
    //
    private Crawler _crawler;

    //
    // Limit of items to retrieve at a time.
    //
    private static final int LIMIT = 10;


    //
    // Config files for our properties and
    // bans.
    // NOTE these paths are relative to the botkernel working directory,
    // as we do not run the bot from here.
    //
    private static final String BANS_FILE = 
                        "../casinobots/scratch/blackjackbot/bans.txt";
    private static final String CONFIG_FILE = 
                        "../casinobots/scratch/blackjackbot/config.properties";

    private User            _user;
    private BlackjackEngine _engine;

    private int _activeCycles;

    private Date _startTime;

    private List<String> _banList;

    private boolean _shutdown;

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
    public BlackjackBot() { }

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
            log("Loading blackjack config properties...");
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            props.load(in);
            in.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            log("ERROR init()'ing " + BOT_NAME);
        }

        //
        // Get user info from properties file
        //
        String username = props.getProperty("username");
        String password = props.getProperty("password");

        String subreddit = props.getProperty("subreddit");

        _user   = new User(username, password);        
        _engine = new BlackjackEngine();

        _startTime      = new Date();
        _banList        = new ArrayList<String>();

        // Connect
        try {
            _user.connect();
        } catch(IOException ioe) {
            log("ERROR conecting user for " + BOT_NAME);
        }

        //
        // Init bans from file.
        //
        PersistenceUtils.loadList(BANS_FILE, _banList, -1);
        log("Loaded bans:               " + _banList.size()  );

        //
        // Create a Crawler thread we will need for crawling our own
        // sub (i.e. RoboCasino)
        //
        List<String> subReddits = new ArrayList<String>();
        subReddits.add(subreddit);

        _crawler = new Crawler( _user,
                                CRAWLER_NAME,
                                subReddits,
                                new Submissions.ListingType[] {
                                        Submissions.ListingType.HOT,
                                        Submissions.ListingType.NEW },
                                LIMIT,
                                MINUTE_SLEEP * 3);

        //
        // Register ourselves with the Crawler
        //
        _crawler.addListener(this);

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

                    if(     body.indexOf("blackjack")       != -1
                        ||  body.indexOf("casino")          != -1
                        ||  body.indexOf("poker")           != -1   ) {

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
                    return BlackjackBot.this;
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

        //
        // Connect
        //
        try {
            _user.connect();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            log("Error cannot connect.");
            return;
        }

        //
        // Start our cycle count for inactivity checking.
        //
        _activeCycles = 0;

        /**
         *  Main loop
         */
        while(true) {

            try {

                //
                // Handle any existing games.
                //
                List<Message> messages = Messages.getMessages(
                                                _user, 
                                                Messages.MessageType.UNREAD );
                continueGames(messages);


                //
                // DEBUG
                //
                // break;
                
                // log("Active cycles " + _activeCycles + 
                //    ", deep sleep threshold " + INACTIVITY_THRESHOLD);

                int sleep = SHORT_SLEEP;
                if(messages.size() > 0) {

                    // log("There is activity. Resetting activeCycles count.");
                    //
                    // reset activeCycles.
                    //
                    _activeCycles = 0;
                    lastActivity = new Date();

                    log("Preparing for micro sleep...");
                    sleep = MICRO_SLEEP;

                } else {

                    //
                    // If we've exceeded the active cycle threshold
                    // become "inactive" for long sleep.
                    //
                    if(_activeCycles >= INACTIVITY_THRESHOLD) {
                        // Default sleep time between checking for games.
                        log("Preparing for long sleep...");
                        sleep = LONG_SLEEP;
                    } else {
                        log("Preparing for short sleep...");
                    }

                }


                //
                // log stats
                //
                log("");
                log("Banned subs:   [" + getBanListAsString() + "]");
                log("Games started: " + _gamesStarted );
                log("Games played:  " + _gamesPlayed );
                log("Running since: " + DATE_FORMAT.format(_startTime) );
                log("Last activity: " + DATE_FORMAT.format(lastActivity) );

                log("Sleeping...");
                sleep(sleep);

                _activeCycles++;

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
     * To implement CrawlerListener
     *
     * This should be a new game request.
     *
     */
    public void handleCrawlerEvent(Thing thing) {

        // 
        // This is a crawler hit, so we are starting a new game.
        //

        //
        // Are we banned from the subreddit? If yes, skip it.
        //
        if(_banList.contains(thing.getSubreddit())) {
            log("Ignoring request in BANNED sub " + thing.getSubreddit());
            return;
        }

        //
        // Make sure some other finder thread didn't add
        // this game and start it.
        //
        if(PersistenceUtils.isBotReplied(BOT_NAME, thing.getName())) {
            log("Ignoring already started game: " + thing);
            return; 
        }

        // Ignore my own comments.
        String author = thing.getAuthor();
        if( author != null &&
            author.equals(_user.getUsername())) {

            // log("  Not considering my own comment for " +
            //    "matching game start text...");
            return;
        }

        // Debug
        if(thing instanceof Comment) {
                log("Starting game from comment with: " + 
                                ((Comment)thing).getAuthor());
        }

        // Debug
        if(thing instanceof Submission) {
                log("Starting game from submission with: " + 
                                ((Submission)thing).getAuthor());
        }

        //
        // Get the dealer's hand
        //
        BlackjackHand dealerHand = 
            new BlackjackHand( new BlackjackCard[] { _engine.dealCard() } );
         
        //
        // Get the player's hand
        //
        BlackjackHand playerHand = (BlackjackHand)_engine.dealHand();
        
        //
        // Create message for player
        //
        String message = createGameOutput(dealerHand, playerHand);
           
        //
        // See if we just dealt them a blackjack.
        // If yes, they win.
        // Append message to player with winner info.
        //
        if(playerHand.isBlackjack()) {
                message += "    ...  \n";
                message += "    Game over. You win!  \n";
        }

        //
        // Reply to player.
        //
        try {

            sendComment(thing, message);

            _gamesStarted++;

        } catch(DeletedCommentException dce) {

            log("Ignoring deleted item... " + thing);

        } catch(BannedUserException ioe) {

            String subreddit = thing.getSubreddit();
            addBan(subreddit);

        } catch(IOException ioe) {

            ioe.printStackTrace(); 

            //
            // Some other error replying to comment...
            //
            log("ERROR replying to:\n" + thing);
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

        log("Continuing " + messages.size() + " existing game(s)...");
        
        for(Message message: messages) {

            //
            // Are we banned from here? If yes, skip it.
            //
            String subreddit = message.getSubreddit();
            if( subreddit != null && _banList.contains(subreddit) ) {

                log("Ignoring reply in BANNED sub " + subreddit);

                Messages.markAsRead(_user, message);
                continue;
            }


            String parentId = message.getParentId();
            String body = message.getBody().toLowerCase().trim();

            if(subreddit == null) {

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

            //
            // Ensure I do not reply to messages more than once.
            //
            if(PersistenceUtils.isBotReplied(BOT_NAME, message.getName())) {
                log("Not playing already replied to message\n" + message);
                Messages.markAsRead(_user, message);
                continue;
            }

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

            if( parentBody != null &&
                parentBody.indexOf("Game over.") != -1) {

                //
                // This game is ended. Why are they still replying to us?
                //
                log("Skipping already finished game for reply: " + body
                    + " (author: " + parent.getAuthor() + ")" );

                Messages.markAsRead(_user, message);
                continue;
            }

            BlackjackHand dealerHand = parseHand(   parent.getBody(),
                                                    PARSE_DEALER_SECTION); 
            BlackjackHand playerHand = parseHand(   parent.getBody(),
                                                    PARSE_PLAYER_SECTION); 

            log("Playing game " + "(" + message.getSubreddit() + ")" );
            log("   " + dealerHand);
            log("   " + playerHand);


            // This is kludgy, but not going to bother right now with 
            // more advanced natural language processing than this
            // for now.
           
            if( body.equals("hit") ||
                body.startsWith("hit") ||
                body.indexOf("hit me") != -1) {

                log("    Player hits.");

                playerHand = (BlackjackHand)_engine.dealCard(playerHand);

                String output = createGameOutput(   dealerHand, 
                                                    playerHand  );
                if(playerHand.isBusted()) {
                    output += "    ...  \n";
                    output += "    Game over. You lose.  \n";
                }

                log("   " + dealerHand);
                log("   " + playerHand);

                //
                // Reply to player
                //
                try {

                    sendComment(message, output);
                    PersistenceUtils.setBotReplied(BOT_NAME, message.getName());
                    _gamesPlayed++;

                } catch(DeletedCommentException dce) {

                    log("Ignoring deleted comment... " + message);

                } catch(BannedUserException ioe) {

                    addBan(subreddit);
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
 
            // 
            // Check for stay/stand
            // 
         
            if( body.equals("stay") ||
                body.equals("stand") ||
                body.startsWith("stay") ||
                body.startsWith("stand") ||
                body.startsWith("thats good") ||
                body.startsWith("that's good") ||
                body.startsWith("thats enough") ||
                body.startsWith("that's enough") ||
                body.startsWith("im good") ||
                body.startsWith("i'm good") ) {

                log("    Player stays.");

                //
                // We'll player dealer stands on all 17's
                // http://www.predictem.com/blackjack/dealer.php
                //

                //
                // Flip dealer card.
                //
                dealerHand = (BlackjackHand)_engine.dealCard(dealerHand);
                String output = createGameOutput(   dealerHand, 
                                                    playerHand );

                log("   ...");
                log("   " + dealerHand);
                log("   " + playerHand);

                while(!dealerHand.isBusted()) {
                    //
                    // Check the current state of the dealer's hand.
                    // If they have 17 or more, stop hitting, break
                    // and check scores.
                    //
                    if(dealerHand.getValues()[0].intValue() >= 17) {
                        break;
                    }

                    //
                    // Reached here. Dealer must hit.
                    //
                    output += "    ...  \n";
                    output += "    Dealer must hit.  \n";
                    output += "    ...  \n";

                    dealerHand = (BlackjackHand)_engine.dealCard(dealerHand);
                    output += createGameOutput( dealerHand, playerHand );
                    
                    log("   ...");
                    log("   " + dealerHand);
                    log("   " + playerHand);
                }

                if(dealerHand.isBusted()) {
                    //
                    // I lose. 
                    //
                    output += "    ...  \n";
                    output += "    Game over. You win!  \n";
                } else {
                    //
                    // Check scores.
                    //
                    int playerVal = playerHand.getValues()[0].intValue();
                    int dealerVal = dealerHand.getValues()[0].intValue();
                    if(playerVal == dealerVal) {
                        output += "    ...  \n";
                        output += "    Game over. Push.  \n";
                    } else {
                        if(playerVal > dealerVal) {
                            output += "    ...  \n";
                            output += "    Game over. You win!  \n";
                        } else {
                            output += "    ...  \n";
                            output += "    Game over. You lose.  \n";
                        }
                    }
                }

                //
                // Send the output game state to the user.
                //
                try {

                    sendComment(message, output);
                    PersistenceUtils.setBotReplied(BOT_NAME, message.getName());
                    _gamesPlayed++;

                } catch(DeletedCommentException dce) {

                    log("Ignoring deleted comment... " + message);

                } catch(BannedUserException ioe) {

                    addBan(subreddit);

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

            //
            // TODO add split? add double down? etc...
            //

            log("    Unknown command.");

            //
            // No supported actions (hit, stay) were detected.
            // Send the output 
            //
            String output = createGameOutput( dealerHand, playerHand );
            output += "    ...  \n";
            output += "    Sorry, I don't understand. " +
                        "Try 'hit' or 'stand'.  \n";
            
            try {

                sendComment(message, output);
                PersistenceUtils.setBotReplied(BOT_NAME, message.getName());

            } catch(DeletedCommentException dce) {

                log("Ignoring deleted comment... " + message);

            } catch(BannedUserException ioe) {

                addBan(subreddit);

            } 


            //
            // No supported actions (hit, stay) were detected.
            // Mark message as read and move on.
            //
            Messages.markAsRead(_user, message);

        }

    }

    private static final int PARSE_DEALER_SECTION = 0;
    private static final int PARSE_PLAYER_SECTION = 1;

    /**
     * @param text      Game text
     * @param parseSec  The section of the text to parse. Either one of
     *                  PARSE_DEALER_SECTION or PARSE_PLAYER_SECTION
     */
    private BlackjackHand parseHand(String text, int parseSec) {
        String line = 
            text.split("\\r?\\n")[parseSec].split(": ")[1].split(" \\(")[0];
       
        String[] strCards = null;

        if( line.startsWith(CARD_BACK) ||
            line.startsWith("▩▩") ||
            line.startsWith("██") ||
            line.startsWith("??") ) {

            line = line.split(" ")[1];
            strCards = new String[] { line };
        } else {
            strCards = line.split(" ");
        }

        return (BlackjackHand)_engine.parseCards(strCards);
    }

    /**
     *
     * Draw the game board. This will be the text of the post 
     * we reply to the user with.
     */
    private String createGameOutput(Hand dealerHand, 
                                    Hand playerHand ) {
        return createGameOutput(dealerHand, playerHand, (String)null);
    }

    /**
     *
     * Draw the board. This will be the text of the post 
     * we reply to the user with.
     */
    private String createGameOutput(Hand dealerHand, 
                                    Hand playerHand, 
                                    String message) {
        String ret = "";
        if(dealerHand.getCards().length == 1) {
            ret += "    Dealer hand: " + CARD_BACK + " " + dealerHand + "  \n";
        } else {
            ret += "    Dealer hand: " + dealerHand + "  \n";
        }
        ret += "    Player hand: " + playerHand + "  \n";

        if(message != null) {
            ret += "    " + message + "  \n";
        }
        
        return ret;
    }

   
    /**
     * Send a comment, append the bot's signature.
     */
    private void sendComment(Thing thing, String text) throws IOException {
        text += "\n\n" +
                "----\n" +
                "Commands: hit, stand | " +
                "[Visit Casino](/r/RoboCasino) | " +
                "[Contact My Human](http://www.reddit.com/message/compose/?to=BlackjackPitboss)    ";
                // +
                // "\n\n" +
                // "^^Please ^^remember ^^to ^^tip ^^your ^^dealer  \n";

        Comments.comment(_user, thing, text);
    }

    /**
     *
     * Add a subreddit to our ban set.
     *
     */
    private synchronized void addBan(String subreddit) {
        log("Adding ban for: " + subreddit);
        _banList.add(subreddit);

        PersistenceUtils.saveList(BANS_FILE, _banList);
    }

    /**
     * Return a String representation of the ban set.
     */
    private String getBanListAsString() {
        String ret = "";
        for(String subreddit: _banList) {
            if(ret.equals("")) {
                ret = subreddit;
            } else {
                ret += ", " + subreddit;
            }
        }
        return ret;
    }



}
