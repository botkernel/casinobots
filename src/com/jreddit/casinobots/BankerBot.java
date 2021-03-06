package com.jreddit.casinobots;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.jreddit.botkernel.*;

/**
 *
 * BankerBot
 *
 */
public class BankerBot extends AbstractCasinoBot 
                        implements Bot, CrawlerListener {

    //
    // Unique bot name
    //
    private static final String BOT_NAME = "BANKER_BOT";

    //
    // Default amount of credits to grant.
    //
    private static final int CREDIT_GRANT = 100;

    //
    // Default number of leaders to display
    //
    private static final int DEFAULT_LEADERS_COUNT = 10;

    //
    // Max limit when displaying leaders
    //
    private static final int MAX_LIMIT = 100;

    //
    // Retry limit for RateLimitException
    //
    private static final int RETRY_LIMIT = 5;

    //
    // Config file(s)
    // NOTE these paths are relative to the botkernel working directory,
    // as we do not run the bot from here.
    //
    private static final String CONFIG_FILE = 
                        "../casinobots/scratch/bankerbot/config.properties";


    /**
     *
     * Provide a default no argument constructor for the botkernel to 
     * load this class.
     *
     */
    public BankerBot() { }

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
            log("Loading bankerbot config properties...");
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            props.load(in);
            in.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            log("ERROR init()'ing " + BOT_NAME);
        }

        initProps(props);

        // Connect
        try {
            _user.connect();
        } catch(IOException ioe) {
            log("ERROR conecting user for " + BOT_NAME);
        }

        _crawler = CasinoCrawler.getCrawler();

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

                    if( body.indexOf("bankerbot credits") != -1 ||
                        body.indexOf("bankerbot balance") != -1 ||
                        body.indexOf("bankerbot leaders") != -1 ||
                        body.indexOf("banker credits") != -1 ||
                        body.indexOf("banker balance") != -1 ||
                        body.indexOf("banker leaders") != -1 ) {
                        // 
                        // Found a bank request
                        //
                        return true;
                    }

                    return false;
                }

                public CrawlerListener getCrawlerListener() {
                    return BankerBot.this;
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
                return;
            }

            //
            // Should we have a bot that doesn't implement
            // Runnable? Like a listener-only bot that only
            // responds to events it has registered for like
            // say from a crawler?
            //
            sleep(60 * 60);
        }
    }

    /**
     *
     * To implement CrawlerListener
     *
     * This should be a banker request.
     *
     */
    public void handleCrawlerEvent(CrawlerEvent event) {

        if(event.getType() != CrawlerEvent.CRAWLER_MATCH) {
            return;
        }

        Thing thing = event.getSource();

        // log("Performing common crawler event checks.");

        if(!commonCrawlerEventChecks(thing)) {
            // Common crawler event checks have failed.
            // We do not want to reply to this post or submission.
            return;
        }

        log("Common crawler event checks passed.");

        String body = null;

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

        // If this is still null, don't know what this is. Ignore it.
        if(body == null) {
            return;
        }

        String author = thing.getAuthor();

        log("Banking for user " + author);

        String reply = "";

        if( body.indexOf("bankerbot credits") != -1 ||
            body.indexOf("banker credits") != -1 ) { 
            
            //
            // Grant credits
            //
            Object lock = PersistenceUtils.getDatabaseLock();
            synchronized(lock) {
                int bal = PersistenceUtils.getBankBalance(author);
                if(bal <= 0) {
                    //
                    // Either zero balance or new player (-1)
                    //
                    PersistenceUtils.setBankBalance(author, CREDIT_GRANT); 
                    reply = 
                        "    You have been granted " + CREDIT_GRANT + 
                        " credits.";
                } else {
                    reply = 
                        "    You already have a balance of " + bal + 
                        " credits.  \n" +
                        "    You can request more credits when you run out.  \n";
                }
            }

        } else if(  body.indexOf("bankerbot balance") != -1 || 
                    body.indexOf("banker balance") != -1 ) { 

            //
            // Display balance
            //
            Object lock = PersistenceUtils.getDatabaseLock();
            synchronized(lock) {
                int bal = PersistenceUtils.getBankBalance(author); 
                if(bal <= 0) {
                    PersistenceUtils.setBankBalance(author, CREDIT_GRANT); 
                    reply =
                        "    You currently do not have any credits.  \n" +
                        "    I will grant you " + CREDIT_GRANT + 
                        " credits.  \n" + 
                        "    Your current balance is " + CREDIT_GRANT + 
                        " credits.  \n";
                } else {
                    reply = 
                        "    Your current balance is " + bal + " credits.  \n";
                }
            }

        } else if(  body.indexOf("bankerbot leaders") != -1 || 
                    body.indexOf("banker leaders") != -1 ) {

            //
            // Display leaders
            //
            int limit = DEFAULT_LEADERS_COUNT;

            String pattern = "banker(bot)? leaders (\\d+)";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(body);
            if(m.find()) {
                try {
                    limit = Integer.parseInt(m.group(2));
                } catch(NumberFormatException nfe) {

                }
            }

            if(limit > MAX_LIMIT) {
                limit = MAX_LIMIT;
            }

            reply += "Top " + limit + " players:\n\n";
            
            reply += "Rank|Player|Credits\n";
            reply += "---:|:--|---:\n";

            AccountInfo[] leaders = PersistenceUtils.getBankLeaders(limit);
            for(int i = 0; i < leaders.length; i++) {
                reply += 
                    "" + (i+1) + "|" + leaders[i].getName() +
                        "|" + leaders[i].getBalance() + "\n";
            }
        }


        for(int retry = 0; retry < RETRY_LIMIT; retry++) {

            boolean setReplied = true;

            //
            // Reply to request.
            //
            try {

                sendComment(thing, reply);

            } catch(DeletedCommentException dce) {

                log("Ignoring deleted item... " + thing);

            } catch(BannedUserException ioe) {
           
                ioe.printStackTrace();

            } catch(RateLimitException rle) {
            
                // rle.printStackTrace();
                log("Caught RateLimitException: " + rle.getMessage());
    
                int sleepSecs = rle.getRetryTime();
    
                log("Sleeping " + sleepSecs +
                        " seconds to recover from rate limit exception...");
    
                sleep(sleepSecs);
                
                setReplied = false;
    
            } catch(IOException ioe) {
    
                ioe.printStackTrace(); 
    
                //
                // Some other error replying to comment...
                //
                log("ERROR replying to:\n" + thing);
            }
    
            //
            // Mark message as replied.
            //
            if(setReplied) {
                PersistenceUtils.setBotReplied(BOT_NAME, thing.getName());
                break;
            } 

            //
            // Retry after catching RateLimitException
            //
            log("Caught RateLimitException. Retry " + (retry+1) );

        }

    }


    /**
     *
     * Send a comment, append the bot's signature.
     *
     */
    private void sendComment(Thing thing, String text) throws IOException {
        text += "\n\n" +
                "----\n" +
                "Commands: credits, balance, leaders [number] | " +
                "[Visit Casino](/r/RoboCasino) | " +
                "[Contact My Human](http://www.reddit.com/message/compose/?to=BlackjackPitboss)    ";
                // +
                // "\n\n" +
                // "^^Please ^^remember ^^to ^^tip ^^your ^^dealer  \n";

        Comments.comment(_user, thing, text);
    }



}
