CREATE DATABASE wds;
CREATE ROLE wds WITH LOGIN ENCRYPTED PASSWORD 'wds';
GRANT CREATE ON DATABASE wds TO wds;
