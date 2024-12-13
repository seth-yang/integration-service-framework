CREATE TABLE mqtt_config
(
    name     TEXT NOT NULL PRIMARY KEY,
    url      TEXT NOT NULL,
    userName TEXT NOT NULL,
    password TEXT NOT NULL
);
