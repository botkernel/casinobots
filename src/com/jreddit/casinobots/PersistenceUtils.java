package com.jreddit.casinobots;

import java.io.*;
import java.util.*;

import com.almworks.sqlite4java.*;
import com.jreddit.botkernel.*;

/**
 *
 * Persistence Utilities
 *
 */
public class PersistenceUtils {

    /**
     *
     *  Location of the db file. This will be relative to the
     *  working directory of the botkernel we are running in.
     */
    private static final String DB_FILE = "../casinobots/scratch/bots.db";

    //
    // NOTE How to check for sqlite tables defined in the schema
    //
    // $ sqlite3 scratch/bots.db
    // sqlite> SELECT * FROM sqlite_master WHERE type='table';
    //

    //
    // Disable verbose sqlite logging
    //
    static {
        java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(java.util.logging.Level.OFF);
    }

    private static Object DB_LOCK = new Object();

    /**
     *
     * Expose a DB lock to callers.
     *
     * Not sure about this sqlite library and transactions, so rather than
     * deal with that, in a threaded env, callers can lock at the java level.
     *
     */
    public static Object getDatabaseLock() { return DB_LOCK; }


    /**
     *
     * Load a file into a List.
     *
     * Load the specified file line by line into a list the given
     * number of lines.
     *
     * @param filename  The name of the file
     * @param list      The list on which to add items
     * @param numLines  The max number of lines to read from the file.
     *                  Specify -1 to read all lines
     *
     */
    public static void loadList(    String filename, 
                                    List<String> list, 
                                    int numLines) {
        try {
            FileInputStream fis = new FileInputStream(filename);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);

            int i = 0;
            String line = null;

            while((line = br.readLine()) != null) {
                list.add(line.trim());
                i++;
                if(i == numLines) {
                    break;
                }
            }
            
            br.close();
            isr.close();
            fis.close();

        } catch (IOException e) {
            e.printStackTrace();
            BotKernel.getBotKernel().log("Error loading file " + filename);
        }
    }

    /**
     *  Save a List to a file.
     *
     * @param filename  The name of the file
     * @param list      The list to write to the file
     *
     */
    public static synchronized void saveList(   String filename, 
                                                List<String> list ) {
        try {

            FileOutputStream fos = new FileOutputStream(filename);

            for(int i = 0; i < list.size(); i++) {
                String sub = list.get(i);
                fos.write((sub + "\n").getBytes());
            }

            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * Return true if the bot has replied to the specified thing.
     * False otherwise.
     *
     * @param botName   The name of the bot.
     * @param thingName The name of the Thing.
     *
     */
    public static boolean isBotReplied(String botName, String thingName) {
        synchronized(DB_LOCK) {

            //
            // This might be a bit counter intuitive, but we will default
            // to true here so that the bot doesn't go spam replying
            // if the db connection somehow fails.
            //
            boolean ret = true;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT bot_name, thing_name " +
                    " FROM bot_replies " +
                    " WHERE bot_name = ? AND thing_name = ?");

                try {
                    st.bind(1, botName);
                    st.bind(2, thingName);
                    if(st.step()) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }
   
    /**
     * 
     * Set a thing as having been replied to by the specified bot.
     *
     * @param botName   The name of the bot.
     * @param thingName The name of the Thing.
     *
     */
    public static void setBotReplied(String botName, String thingName) {
        synchronized(DB_LOCK) {

            try {
                //
                // TODO Should this connection be cached rather 
                // than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "INSERT INTO bot_replies (bot_name, thing_name) " +
                    " VALUES (?, ?)" );

                try {
                    st.bind(1, botName);
                    st.bind(2, thingName);
                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }
        }
    }

    /**
     *
     * Query for a player's bank balance.
     *
     * @param player    The name of the player
     *
     * @return The player's balance, or -1 if no balance is present.
     *
     */
    public static int getBankBalance(String player) {

        synchronized(DB_LOCK) {

            int ret = -1;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT player_name, balance " +
                    " FROM bank " +
                    " WHERE player_name = ?");

                try {
                    st.bind(1, player);
                    if(st.step()) {
                        ret = st.columnInt(0);
                    } 
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }

    /**
     * 
     * Set a player's bank balance
     *
     * @param player    The name of the player.
     * @param balance   The balance to set.
     *
     */
    public static void setBankBalance(String player, int balance) {

        synchronized(DB_LOCK) {

            try {
                //
                // TODO Should this connection be cached rather 
                // than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                int existingBal = getBankBalance(player);

                SQLiteStatement st = null;
               
                
                try {
                
                    if(existingBal == -1) {
                        st = db.prepare(
                            "INSERT INTO bank (player_name, balance) " +
                            " VALUES (?, ?)" );
                        st.bind(1, player);
                        st.bind(2, balance);
                    } else {
                        st = db.prepare(
                            "UPDATE bank SET balance = ? " +
                            " WHERE player_name = ?");
                        st.bind(1, balance);
                        st.bind(2, player);
                    }

                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }
        }
    }

    /**
     *
     * Query for leading players
     *
     * @param limit     Number of leaders to retrieve
     *
     * @return The specified limit number of leading players.
     *
     */
    public static AccountInfo[] getBankLeaders(int limit) {

        ArrayList<AccountInfo> ret = new ArrayList<AccountInfo>();

        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT player_name, balance " +
                    " FROM bank " +
                    " ORDER BY balance DESC LIMIT ?");
                
                try {
                    st.bind(1, limit);
                    while(st.step()) {
                        String name = st.columnString(0);
                        int bal = st.columnInt(1);
                        AccountInfo info = new AccountInfo(name, bal);
                        ret.add(info);
                    } 
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return (AccountInfo[])ret.toArray(new AccountInfo[0]);
        }
    }



}
