CREATE TABLE bot_replies 
(
    bot_name    varchar(128),
    thing_name  varchar(20)
);

CREATE INDEX replies_index on bot_replies (thing_name);

