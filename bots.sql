CREATE TABLE bot_replies 
(
    bot_name    varchar(128),
    thing_name  varchar(20)
);

CREATE INDEX replies_index on bot_replies (thing_name);

CREATE TABLE bank 
(
    player_name     varchar(128),
    balance         INTEGER
);

CREATE INDEX bank_player_index on bank (player_name);

