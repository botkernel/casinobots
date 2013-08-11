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
 * CasinoCrawler
 *
 * Crawl the casino subreddit and look for posts requiring bot actions.
 *
 * The bots should register listeners with this crawler to receive 
 * notifications of criteria matches for reponding.
 *
 */
public class CasinoCrawler extends Crawler {

    //
    // Unique crawler name
    //
    private static final String CRAWLER_NAME = "CASINO_CRAWLER";

    //
    // The sleep time for the Crawler we will register
    //
    private static final int SLEEP = 10;

    //
    // Limit of items to retrieve at a time.
    //
    private static final int LIMIT = 10;

    //
    // This is our crawler, which looks for new game requests or other 
    // posts our bots might respond to.
    //
    private static CasinoCrawler _crawler;

    //
    // Config file for our casino crawler
    //
    private static final String CONFIG_FILE = 
                    "../casinobots/scratch/casinocrawler/config.properties";

    /**
     *
     * Get the Crawler singleton instance
     *
     */
    public static synchronized CasinoCrawler getCrawler() {

        if(_crawler != null) {
            return _crawler;
        } 

        Properties props = new Properties();
        try {
            log("Loading casino crawler config properties...");
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            props.load(in);
            in.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            log("ERROR init()'ing " + CRAWLER_NAME);
        }

        //
        // Get user info from properties file
        //
        String username = props.getProperty("username");
        String password = props.getProperty("password");

        String subreddit = props.getProperty("subreddit");

        User user   = new User(username, password);        

        //
        // Connect
        //
        try {
                user.connect();
        } catch(IOException ioe) {
                log("ERROR conecting user for " + CRAWLER_NAME);
        }

        List<String> subReddits = new ArrayList<String>();
        subReddits.add(subreddit);

        _crawler = new CasinoCrawler( 
                                user,
                                CRAWLER_NAME,
                                subReddits,
                                new Submissions.ListingType[] {
                                        Submissions.ListingType.HOT,
                                        Submissions.ListingType.NEW },
                                LIMIT,
                                SLEEP);
        return _crawler;
    }

    private CasinoCrawler(  User user,
                            String name,
                            List<String> subs,
                            Submissions.ListingType[] listingTypes,
                            int limit,
                            int sleepTime ) {
 
        super(  user,
                name,
                subs,
                listingTypes,
                limit,
                sleepTime );
    }

}
