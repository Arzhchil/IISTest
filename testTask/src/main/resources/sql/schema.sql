DROP TABLE IF EXISTS Positions;

CREATE TABLE Positions (
    ID SERIAL PRIMARY KEY,
    DepCode VARCHAR(20) NOT NULL,
    DepJob VARCHAR(100) NOT NULL,
    Description VARCHAR(255),
    UNIQUE (DepCode, DepJob)
);