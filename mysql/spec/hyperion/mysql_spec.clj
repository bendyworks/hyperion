(ns hyperion.mysql-spec
  (:require [speclj.core :refer :all]
            [hyperion.api :refer :all]
            [hyperion.log :as log]
            [hyperion.sql.spec-helper :refer :all]
            [hyperion.dev.spec :refer [it-behaves-like-a-datastore]]
            [hyperion.sql.transaction-spec :refer [include-transaction-specs]]
            [hyperion.sql.connection :refer [with-connection]]
            [hyperion.sql.jdbc :refer [execute-mutation]]
            [hyperion.sql.query :refer :all]
            [hyperion.mysql]))

(log/error!)

(defn do-query [query]
  (execute-mutation
    (make-query query)))

(def create-table-query
  "CREATE TABLE IF NOT EXISTS %s (
    id INTEGER NOT NULL AUTO_INCREMENT,
    name VARCHAR(35),
    first_name VARCHAR(35),
    inti INTEGER,
    data VARCHAR(32),
    PRIMARY KEY (id)
  )")

(defn create-key-tables []
  (do-query
    "CREATE TABLE IF NOT EXISTS account (
    id INTEGER NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(35),
    inti INTEGER,
    data VARCHAR(32),
    PRIMARY KEY (id)
    );")
  (do-query
    "CREATE TABLE IF NOT EXISTS shirt (
    id INTEGER NOT NULL AUTO_INCREMENT,
    account_id INTEGER,
    first_name VARCHAR(35),
    inti INTEGER,
    data VARCHAR(32),
    PRIMARY KEY (id),
    INDEX (account_id),
    FOREIGN KEY (account_id) REFERENCES account (id)
    )"))

(defn create-types-table []
  (do-query
    "CREATE TABLE IF NOT EXISTS types (
    id INTEGER NOT NULL AUTO_INCREMENT,
    bool BOOLEAN,
    bite TINYINT,
    inti INTEGER,
    lng BIGINT,
    big_int BLOB,
    flt DOUBLE,
    dbl DOUBLE,
    data VARCHAR(32),
    first_name VARCHAR(35),
    PRIMARY KEY (id)
    )"))

(defentity :types
  [bool]
  [bite :type java.lang.Byte]
  [inti]
  [lng]
  [flt :type java.lang.Float]
  [dbl])

(defn create-table [table-name]
  (do-query (format create-table-query table-name)))

(defn drop-table [table-name]
  (do-query (format "DROP TABLE IF EXISTS %s" table-name)))

(def connection-url "jdbc:mysql://localhost:3306/hyperion?user=root")

(describe "MySQL Datastore"

  (it "with factory fn"
    (let [ds (new-datastore :implementation :mysql :connection-url connection-url :database "hyperion")]
      (should= "hyperion" (.database (.db ds)))))

  (context "live"

    (around [it]
      (binding [*ds* (new-datastore :implementation :mysql :connection-url connection-url :database "hyperion")]
        (it)))

    (before
      (with-connection connection-url
        (create-table "testing")
        (create-table "other_testing")
        (create-key-tables)
        (create-types-table)))

    (after
      (with-connection connection-url
        (drop-table "testing")
        (drop-table "other_testing")
        (drop-table "shirt")
        (drop-table "account")
        (drop-table "types")))

    (it-behaves-like-a-datastore)

    (context "Transactions"
      (include-transaction-specs connection-url))

    (context "SQL Injection"
      (it "sanitizes strings to be inserted"
        (let [evil-string "my evil string' --"
              record (save {:kind :testing :name evil-string})]
          (should= evil-string (:name (find-by-key (:key record))))))

      (it "sanitizes table names"
        (error-msg-contains?
          "Table 'hyperion.my_evil_name`___' doesn't exist"
          (save {:kind "my-evil-name` --" :name "test"})))

      (it "sanitizes column names"
        (error-msg-contains?
          "Unknown column 'my_evil_name`___' in 'field list'"
          (save {:kind :testing (keyword "my-evil-name` --") "test"}))))

    )
  )
