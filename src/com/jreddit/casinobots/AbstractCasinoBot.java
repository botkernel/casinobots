package com.jreddit.casinobots;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.jreddit.botkernel.*;

/**
 *
 * Abstract casino bot base class.
 *
 * This has some common functionality shared across all casino bots.
 * Probably need a bunch of refactoring to move more in here.
 *
 */
public abstract class AbstractCasinoBot extends BaseBot  {

    //
    // The user for this bot
    //
    protected User _user;

    //
    // A reference to our CasinoCrawler so that we can unregiste rourselves
    // if necessary.
    //
    protected Crawler _crawler;

    protected Date _startDate;

    //
    // The list of users this bot should not reply to.
    //
    protected List<String> _ignoreUsers = new ArrayList<String>();

    /**
     *
     * Perform common init tasks from properties object loaded from file.
     *
     */
    protected void initProps(Properties props) {

        //
        // The oldest date at which this bot will respond to
        // either messages or crawler hits. This prevents us from spamming
        // old messages or hits if the kernel daemon is stopped and then 
        // restarted. Our comment reply DB will also help with this.
        // This could be both an optimization, and help us deciding what to
        // reply to when coming up after downtime.
        //
        // Fall back to using "new Date()" if this bot has never 
        // replied to anyone
        //
        _startDate = new Date();
        try {
            List<Comment> comments = Comments.getUserComments(
                                            _user,
                                            _user.getUsername(),
                                            1 );
            if(comments.size() > 0) {
                Comment comment = comments.get(0);
                Date d = comment.getCreatedDate();
                if(d != null) {
                    _startDate = d;
                }
            }
        } catch( IOException ioe) {
            log("Could not find last comment posted for " + 
                _user.getUsername());
        }

        //
        // Get user info from properties file
        //
        String username = props.getProperty("username");
        String password = props.getProperty("password");

        _user   = new User(username, password);

        String ignoreUsers = props.getProperty("ignoreUsers");
        if(ignoreUsers != null) {
            String[] ignores = ignoreUsers.split(",");
            for(String ignore: ignores) {
                _ignoreUsers.add(ignore);
            }
        }
    }

    /**
     *
     * Common checks on crawler true.
     *
     * Common checks all casino bots will perform on a crawler hit
     * before attempting to reply. Allows subclasses to call up into this
     * method to avoid code duplication.
     *
     * @return true if all checks passed, false otherwise.
     */
    protected boolean commonCrawlerEventChecks(Thing thing) {

        //
        // Ignore posts which were created prior to our last comment.
        // _startDate is the earliest date of a post we shoudl reply to.
        //
        Date d = thing.getCreatedDate();
        if(d == null) {
            return false;
        }
        if(d.before(_startDate)) {
            //
            // In the event that the database was destroyed since
            // last start, ignore these messages in order to avoid
            // massive spamming
            //
            log("Ignoring old message from " + d);
            return false;
        }

        //
        // Ignore already handled requests
        //
        if(PersistenceUtils.isBotReplied(getName(), thing.getName())) {
            // log("Ignoring already handled request: " + thing);
            return false;
        }

        //
        // Ignore comments the bot itself has made.
        //       
        String author = thing.getAuthor();

        //
        // if author is null (deleted?) can't do anything
        //
        if(author == null) {
            return false;
        }

        if( author.equals(_user.getUsername()) ) {
            // log("  Not considering my own comment.");
            return false;
        }

        //
        // Check for other authors to ignore.
        //
        if( _ignoreUsers.contains(author) ) {
            // log("Ignoring comment from " + author);
            return false;
        }

        return true;
    }
    
}


