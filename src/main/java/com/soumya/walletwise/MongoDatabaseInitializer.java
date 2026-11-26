package com.soumya.walletwise;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Creates the MongoDB collections and indexes used by the original WalletWise schema. */
@Component
public class MongoDatabaseInitializer implements ApplicationRunner {
    private final MongoTemplate mongo;

    public MongoDatabaseInitializer(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void run(ApplicationArguments args) {
        createCollection("users");
        createCollection("transactions");
        createCollection("budgets");
        createCollection("savingGoals");
        createCollection("subscriptions");
        createCollection("investments");
        createCollection("wallets");
        createCollection("transactionActivities");

        // Partial indexes allow an existing database to contain documents that
        // predate these optional fields while keeping populated values unique.
        uniqueString("users", new Document("email", 1), "email");
        uniqueString("users", new Document("studentId", 1), "studentId");
        unique("budgets", new Document("userId", 1).append("month", 1));
        index("transactions", new Document("userId", 1).append("type", 1).append("date", -1));
        index("transactions", new Document("userId", 1).append("category", 1));
        index("transactions", new Document("userId", 1).append("description", 1));
    }

    private void createCollection(String name) { if (!mongo.collectionExists(name)) mongo.createCollection(name); }
    private void index(String collection, Document keys) { safelyCreateIndex(() -> mongo.getCollection(collection).createIndex(keys)); }
    private void uniqueString(String collection, Document keys, String field) {
        safelyCreateIndex(() -> mongo.getCollection(collection).createIndex(keys, new IndexOptions().unique(true)
                .partialFilterExpression(new Document(field, new Document("$type", "string")))));
    }
    private void unique(String collection, Document keys) { safelyCreateIndex(() -> mongo.getCollection(collection).createIndex(keys, new IndexOptions().unique(true))); }
    private void safelyCreateIndex(Runnable operation) {
        try { operation.run(); }
        // An existing MongoDB deployment can already have the original index
        // definition. It is safe to preserve that index rather than fail startup.
        catch (com.mongodb.MongoCommandException ignored) { }
    }
}
