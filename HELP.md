# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.3.2/maven-plugin)
- [Create an OCI image](https://docs.spring.io/spring-boot/3.3.2/maven-plugin/build-image.html)
- [Spring Security](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#web.security)
- [Spring Data JPA](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#data.sql.jpa-and-spring-data)
- [Rest Repositories](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#howto.data-access.exposing-spring-data-repositories-as-rest)

### Guides

The following guides illustrate how to use some features concretely:

- [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
- [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
- [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
- [Accessing JPA Data with REST](https://spring.io/guides/gs/accessing-data-rest/)
- [Accessing Neo4j Data with REST](https://spring.io/guides/gs/accessing-neo4j-data-rest/)
- [Accessing MongoDB Data with REST](https://spring.io/guides/gs/accessing-mongodb-data-rest/)

### Install Java 17

Please consider installing Java jdk 17 if not already installed into your system. Use the command `java --version` to check the version.

To install jdk-17 execute the command:

```
apt install openjdk-17-jdk openjdk-17-jre
```

Verify that `JAVA_HOME` is set by executing `echo $JAVA_HOME`.

Find the jdk installation path with the following command

```
sudo update-alternatives --config java
```

Set `JAVA_HOME` into your `.bashrc`:

```
nano ~/.bashrc
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
source ~/.bashrc
```

### Maven Parent overrides

You can install maven with APT

```
sudo apt install maven -y
```

Check the installation with

```
mvn --version
```

# DATABASE (POSGRESQL)

## Install PostgreSQL Linux (Ubuntu)

For downloading postgreSQL please follow the link [here](https://www.postgresql.org/download/linux/ubuntu/).

Or we can install it simply with apt by executing the following command:

```
sudo apt -y install postgresql-14
```

PostgreSLQ server can be configured to listen on some addresses for remote connections. This is done by editing the `postgresql.conf` file.

```
sudo nano /etc/postgresql/14/main/postgresql.conf

listen_addresses = '*'
```

We then configure `pg_hba.conf` file to use md5 password auth for remote connection.

```
sudo sed -i '/^host/s/ident/md5/' /etc/postgresql/14/main/pg_hba.conf
sudo sed -i '/^local/s/peer/trust/' /etc/postgresql/14/main/pg_hba.conf
echo "host all all 0.0.0.0/0 md5" | sudo tee -a /etc/postgresql/14/main/pg_hba.conf
```

Resart PostgreSQL

```
sudo systemctl restart postgresql
```

### Connect to the PostgreSQL database server

Connect to PostgreSQL through `postgres` user:

```
sudo -u postgres psql
```

We have to alter the password for `postgres` user:

```
ALTER USER postgres PASSWORD '<password>';
```

Create a new user:

```
postgres=# CREATE USER <username> WITH PASSWORD '<password>';
CREATE ROLE
```

We can also create a database for our created user:

```
postgres=# CREATE DATABASE <databas_name> OWNER <username>;
CREATE DATABASE
```

### Sql create sequence with Postgres

When you use spring boot you can choose a strategy to set primary key value by creating sequence generator. This is the advanced version of the default auto-increment.

To create a new sequence in postgres use the following:

```
DROP SEQUENCE IF EXISTS serial;
CREATE SEQUENCE serial START 101;
```

Select the next number from this sequence:

```
SELECT nextval('serial');
```

Update the current value of the sequence:

```
SELECT setval('serial', 201);
```

Some time we might be in case where data are imported from `csv` file. Importing them won't update the current value of the sequence (`SELECT currval('serial')`). Thus, might lead to violation of the primary key during insertion with Spring JPA.

To avoid that we can update the sequence as fellow:

```
SELECT setval('serial', max(id)) FROM nk_comment;
```

## Full Text Search

We use postgres's full text search (or text search) as query search. This offers more powerful features for searching than the common `LIKE` operator. Its purpose is to find all documents (which can be tuples) that contains the given query and return them in order of relevance. Please refer to the [documentation](https://www.postgresql.org/docs/current/textsearch-intro.html) for more details.

We consider using it instead of more search document indexing database such as Elasticsearch as to avoid some complexity at the begging. It might be good to reconsider later in the development.

The motivation behind the usage of this feature has much to do with the limitation of existing with the other operators such as `~`, `~*`, `LIKE`, and `ILIKE`:

- They provide no ordering (ranking) of search results, which makes them ineffective when thousands of matching documents are found.
- Cannot easily handle derived words, e.g., satisfies and satisfy are similar.
- They tend to be slow because there is no index support, so they must process all documents for every search.

With this approach, text can we preprocess and index as data are updating by :

- Parsing document into tokens, which represents small fragments of words, sentences, etc.
- Converting tokens into lexemes which consist of normalizing the tokens so that different forms of the same word are made alike,
- And then storing preprocessed documents optimized for searching

A **document** is normally a textual field within a row of a database table, or possibly a combination (concatenation) of such fields, perhaps stored in several tables or obtained dynamically.
Full text searches can be accelerated using indexes.

So, let's implement our full text search.

### Creating indexes

We will first create some indexes to speed up text searches. In our case indexes will be on table `nk_channel` to target `name` and `nk_post` with `title`, `content`, `keywords`.

```
ALTER TABLE nk_post
    ADD COLUMN textsearchable_index_col tsvector
               GENERATED ALWAYS AS (
                    setweight(to_tsvector('french', title), 'A') || '' ||
                    setweight(to_tsvector('french', content), 'B') || '' ||
                    setweight(to_tsvector('french', coalesce(keywords, '')), 'D')
                  ) STORED;
```
With that only `WHERE to_tsvector('french', content) @@ 'a & b'` will use the index and `WHERE to_tsvector(body) @@ 'a & b'` cannot.

Then we create a GIN index to speed up the search:

```
CREATE INDEX nk_post_textsearch_idx ON nk_post USING GIN (textsearchable_index_col);
CREATE INDEX nk_post_status_idx ON nk_post (status);
```
Note that we create `nk_post_status_idx` index to filter out posts that are not validated while avoiding loading all the table.

Now let's perform a test to make sure that everything is fine:
```
EXPLAIN ANALYZE
SELECT title, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM nk_post, websearch_to_tsquery('vestigium') query
WHERE status = 'VALIDATED' AND textsearchable_index_col @@ query
ORDER BY rank DESC
LIMIT 10;
```

The query plan output is :
```
Limit  (cost=22.70..22.71 rows=1 width=22) (actual time=0.238..0.244 rows=6 loops=1)
  ->  Sort  (cost=22.70..22.71 rows=1 width=22) (actual time=0.236..0.240 rows=6 loops=1)
        Sort Key: (ts_rank_cd(nk_post.textsearchable_index_col, query.query)) DESC
        Sort Method: quicksort  Memory: 25kB
        ->  Nested Loop  (cost=18.66..22.69 rows=1 width=22) (actual time=0.187..0.224 rows=6 loops=1)
              ->  Function Scan on websearch_to_tsquery query  (cost=0.25..0.26 rows=1 width=32) (actual time=0.063..0.064 rows=1 loops=1)
              ->  Bitmap Heap Scan on nk_post  (cost=18.41..22.42 rows=1 width=50) (actual time=0.110..0.131 rows=6 loops=1)
                    Recheck Cond: (((status)::text = 'VALIDATED'::text) AND (textsearchable_index_col @@ query.query))
                    Heap Blocks: exact=6
                    ->  BitmapAnd  (cost=18.41..18.41 rows=1 width=0) (actual time=0.093..0.094 rows=0 loops=1)
                          ->  Bitmap Index Scan on nk_post_status_idx  (cost=0.00..6.05 rows=236 width=0) (actual time=0.042..0.042 rows=236 loops=1)
                                Index Cond: ((status)::text = 'VALIDATED'::text)
                          ->  Bitmap Index Scan on nk_post_textsearch_idx  (cost=0.00..12.05 rows=7 width=0) (actual time=0.031..0.031 rows=42 loops=1)
                                Index Cond: (textsearchable_index_col @@ query.query)
Planning Time: 0.295 ms
Execution Time: 0.309 ms
```

---

Now let's move to channel as we would like also the query to consider the `nk_channel`.

```
ALTER TABLE nk_channel
    ADD COLUMN textsearchable_index_col tsvector
               GENERATED ALWAYS AS (
                    setweight(to_tsvector('french', name), 'A')
                  ) STORED;
```

Then we create a GIN index to speed up the search:

```
CREATE INDEX nk_channel_textsearch_idx ON nk_channel USING GIN (textsearchable_index_col);
```

```
EXPLAIN ANALYZE
SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM nk_channel, websearch_to_tsquery('vestigium') query
WHERE textsearchable_index_col @@ query
ORDER BY rank DESC;
LIMIT 10;
```

### Some queries to retrieve some posts

Now we can put all together and check if everything is fine :

```
EXPLAIN ANALYZE
SELECT p.* FROM (
  SELECT *, ts_rank_cd(textsearchable_index_col, query) AS rank
  FROM nk_post, websearch_to_tsquery('vestigium') query
  WHERE status='VALIDATED' AND textsearchable_index_col @@ query
  ) AS p
LEFT JOIN (SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM nk_post, websearch_to_tsquery('vestigium') query
WHERE textsearchable_index_col @@ query) AS a
ON p.channel_id=a.id
ORDER BY a.rank,p.rank DESC
LIMIT 100;
```

```
Limit  (cost=25.63..25.64 rows=1 width=428) (actual time=0.077..0.078 rows=1 loops=1)
  ->  Sort  (cost=25.63..25.64 rows=1 width=428) (actual time=0.076..0.077 rows=1 loops=1)
        Sort Key: (ts_rank_cd(nk_post_1.textsearchable_index_col, query_1.query)), (ts_rank_cd(nk_post.textsearchable_index_col, query.query)) DESC
        Sort Method: quicksort  Memory: 27kB
        ->  Nested Loop  (cost=19.18..25.62 rows=1 width=428) (actual time=0.072..0.073 rows=1 loops=1)
              Join Filter: (nk_post_1.textsearchable_index_col @@ query_1.query)
              Rows Removed by Join Filter: 5
              ->  Nested Loop  (cost=18.93..25.35 rows=1 width=452) (actual time=0.050..0.065 rows=6 loops=1)
                    ->  Nested Loop  (cost=18.66..22.69 rows=1 width=420) (actual time=0.047..0.053 rows=6 loops=1)
                          ->  Function Scan on websearch_to_tsquery query  (cost=0.25..0.26 rows=1 width=32) (actual time=0.016..0.016 rows=1 loops=1)
                          ->  Bitmap Heap Scan on nk_post  (cost=18.41..22.42 rows=1 width=388) (actual time=0.029..0.035 rows=6 loops=1)
                                Recheck Cond: (((status)::text = 'VALIDATED'::text) AND (textsearchable_index_col @@ query.query))
                                Heap Blocks: exact=6
                                ->  BitmapAnd  (cost=18.41..18.41 rows=1 width=0) (actual time=0.024..0.025 rows=0 loops=1)
                                      ->  Bitmap Index Scan on nk_post_status_idx  (cost=0.00..6.05 rows=236 width=0) (actual time=0.010..0.010 rows=236 loops=1)
                                            Index Cond: ((status)::text = 'VALIDATED'::text)
                                      ->  Bitmap Index Scan on nk_post_textsearch_idx  (cost=0.00..12.05 rows=7 width=0) (actual time=0.009..0.009 rows=42 loops=1)
                                            Index Cond: (textsearchable_index_col @@ query.query)
                    ->  Index Scan using nk_post_pkey on nk_post nk_post_1  (cost=0.28..2.65 rows=1 width=40) (actual time=0.002..0.002 rows=1 loops=6)
                          Index Cond: (id = nk_post.channel_id)
              ->  Function Scan on websearch_to_tsquery query_1  (cost=0.25..0.26 rows=1 width=32) (actual time=0.000..0.000 rows=1 loops=6)
Planning Time: 0.220 ms
Execution Time: 0.106 ms
```

We can create a view from that :
```
CREATE VIEW full_text_search_post AS
SELECT p.* FROM (
  SELECT *, ts_rank_cd(textsearchable_index_col, query) AS rank
  FROM nk_post, websearch_to_tsquery('vestigium') query
  WHERE status='VALIDATED' AND textsearchable_index_col @@ query
  ) AS p
LEFT JOIN (SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM nk_post, websearch_to_tsquery('vestigium') query
WHERE textsearchable_index_col @@ query) AS a
ON p.channel_id=a.id
ORDER BY a.rank,p.rank DESC
LIMIT 100;

EXPLAIN ANALYZE
SELECT
  full_search.title,
  p.title AS post_reference_title,
  p.content AS post_reference_content,
  a.name AS channel_name
FROM (
  SELECT p.* FROM (
    SELECT *, ts_rank_cd(textsearchable_index_col, query) AS rank
    FROM nk_post, websearch_to_tsquery('vestigium creator') query
    WHERE status='VALIDATED' AND textsearchable_index_col @@ query
    ) AS p
  LEFT JOIN (SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank
  FROM nk_post, websearch_to_tsquery('vestigium creator') query
  WHERE textsearchable_index_col @@ query) AS a
  ON p.channel_id=a.id
  ORDER BY a.rank,p.rank DESC
  LIMIT 100
) AS full_search
LEFT JOIN nk_post AS p ON full_search.post_reference_id = p.id
LEFT JOIN nk_channel AS a ON a.id = p.channel_id;
```

## Getting started with PostgreSQL (linux teminal only)

### Connect to a PostgreSQL Database Server (via psql)

The `psql` is a terminal-based utility to connect to the PostgreSQL server. It is used for interacting with PostgreSQL server such as executing SQL statements and managing database objects.

First, you can connect as postgress user

```
sudo -i -u postgres

# or

psql -U postgres

# or simply

psql
```

where: -`psql`: Invoke the psql program -`-U` postgres: Specify the user that connects to the PostgreSQL server. The -U option means user. Note that you need to use -U in uppercase, not lowercase.

### Create and manage a database

Create a new database with `CREATE DATABASE <database-name>`

```
postgres=# CREATE DATABASE ngelmak_project;
```

We can list all databases

```
postgres=# \l
```

Connect to the created database

```
postgres=# \c ngelmak_project
You are now connected to database "ngelmak_project" as user "postgres".
ngelmak_project=#
```

Display all tables (relations) from the connect databases

```
ngelmak_project=# \dt
```

Please refers to [PostgreSQL tutorial for more details](https://www.postgresqltutorial.com/postgresql-getting-started/connect-to-postgresql-database/).