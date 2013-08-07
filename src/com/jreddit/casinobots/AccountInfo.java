package com.jreddit.casinobots;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;


/**
 *
 * Represents a player's bank info
 *
 */
public class AccountInfo {

    private String name;
    private int balance;

    public AccountInfo(String name, int balance) {
        this.name = name;
        this.balance = balance;
    }

    public String getName() {
        return name;
    }

    public int getBalance() {
        return balance;
    }

}
