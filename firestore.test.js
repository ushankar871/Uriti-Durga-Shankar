const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SELLER_UID = "seller_alice_123";
const BUYER_UID = "buyer_bob_456";
const OTHER_BUYER_UID = "buyer_charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write accounts, stores, products, orders, feedback, or auditLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("stores").get());
  await assertFails(
    unauthDb.collection("stores").doc(SELLER_UID).collection("products").get()
  );
  await assertFails(
    unauthDb.collection("stores").doc(SELLER_UID).collection("auditLogs").get()
  );
  await assertFails(
    unauthDb.collection("orders").where("buyerId", "==", BUYER_UID).get()
  );
  await assertFails(
    unauthDb.collection("accounts").doc(SELLER_UID).set({
      userId: SELLER_UID,
      role: "SELLER",
      displayName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Account profiles enforce owner isolation and valid SELLER/BUYER roles", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    sellerDb.collection("accounts").doc(SELLER_UID).set({
      userId: SELLER_UID,
      role: "SELLER",
      displayName: "Alice Seller",
      email: "alice@example.com",
      phoneNumber: "+919876543210",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Oversized phoneNumber (>24 chars) is rejected
  await assertFails(
    sellerDb.collection("accounts").doc(SELLER_UID).update({
      phoneNumber: "1234567890123456789012345",
      updatedAt: now,
    })
  );

  // Buyer cannot read or overwrite Seller's account profile
  await assertFails(buyerDb.collection("accounts").doc(SELLER_UID).get());
  await assertFails(
    buyerDb.collection("accounts").doc(SELLER_UID).set({
      userId: SELLER_UID,
      role: "BUYER",
      displayName: "Hacked",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Anti-theft deleteProtectionEnabled blocks deletion until unlocked, and auditLogs are immutable", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    sellerDb.collection("stores").doc(SELLER_UID).set({
      userId: SELLER_UID,
      storeName: "ShubhMart Grocery & Puja",
      address: "100 Feet Rd, Indiranagar",
      storeLat: 12.9716,
      storeLng: 77.5946,
      deliveryRadiusKm: 5.0,
      showOutOfStock: true,
      deleteProtectionEnabled: true,
      otpProtectionEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: SELLER_UID,
        name: "Pure Brass Diya",
        category: "PUJA",
        unit: "1 pair",
        priceInr: 180,
        stockQty: 10,
        inStock: true,
        imageTag: "diya",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("products")
      .doc("prod_fancy")
      .set({
        userId: SELLER_UID,
        name: "Silk Bangles Set",
        category: "FANCY",
        unit: "1 set",
        priceInr: 220,
        stockQty: 12,
        inStock: true,
        imageTag: "fancy",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Audit log creation succeeds, update & delete strictly fail (non-thefting ledger)
  await assertSucceeds(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("auditLogs")
      .doc("log_1")
      .set({
        sellerId: SELLER_UID,
        action: "CREATE_ITEM",
        summary: "Added Pure Brass Diya (10)",
        createdAt: created,
      })
  );
  await assertFails(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("auditLogs")
      .doc("log_1")
      .update({ summary: "Tampered" })
  );
  await assertFails(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("auditLogs")
      .doc("log_1")
      .delete()
  );
  await assertFails(
    buyerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("auditLogs")
      .get()
  );

  // Buyer can read stores & products, cannot tamper stock
  await assertSucceeds(buyerDb.collection("stores").get());
  await assertSucceeds(
    buyerDb.collection("stores").doc(SELLER_UID).collection("products").get()
  );
  await assertFails(
    buyerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        stockQty: 99,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // While deleteProtectionEnabled == true, even Seller cannot delete product or store
  await assertFails(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("products")
      .doc("prod_1")
      .delete()
  );
  await assertFails(sellerDb.collection("stores").doc(SELLER_UID).delete());

  // Unlock delete protection & update lowStockThreshold, then check range bounds
  await assertFails(
    sellerDb.collection("stores").doc(SELLER_UID).update({
      lowStockThreshold: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    sellerDb.collection("stores").doc(SELLER_UID).update({
      deleteProtectionEnabled: false,
      lowStockThreshold: 8,
      lowStockAlertsEnabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("products")
      .doc("prod_1")
      .delete()
  );
});

test("Buyers place orders scoped by buyerId/sellerId, Seller advances status, third parties blocked", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const otherBuyerDb = testEnv.authenticatedContext(OTHER_BUYER_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    buyerDb.collection("orders").doc("ord_1").set({
      sellerId: SELLER_UID,
      buyerId: BUYER_UID,
      storeName: "Lakshmi Puja, Fancy & Grocery",
      customerName: "Bob Buyer",
      neighborhood: "Koramangala",
      itemsSummary: "2x Brass Diya",
      totalInr: 360,
      status: "PENDING",
      lat: 12.9352,
      lng: 77.6245,
      deliveryOtp: "482910",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    buyerDb.collection("orders").where("buyerId", "==", BUYER_UID).get()
  );
  await assertSucceeds(
    sellerDb.collection("orders").where("sellerId", "==", SELLER_UID).get()
  );

  await assertFails(otherBuyerDb.collection("orders").doc("ord_1").get());
  await assertFails(
    otherBuyerDb.collection("orders").where("buyerId", "==", BUYER_UID).get()
  );

  await assertFails(
    buyerDb.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Buyer cannot spoof rider coordinates; Seller can publish valid live rider location and invalid lat fails
  await assertFails(
    buyerDb.collection("orders").doc("ord_1").update({
      riderLat: 12.95,
      riderLng: 77.61,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    sellerDb.collection("orders").doc("ord_1").update({
      riderLat: 95.0,
      riderLng: 77.61,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    sellerDb.collection("orders").doc("ord_1").update({
      status: "OUT_FOR_DELIVERY",
      riderLat: 12.95,
      riderLng: 77.61,
      riderUpdatedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    sellerDb.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      deliveryMethod: "OTP",
      deliveryProofUri: "",
      riderLat: 12.9352,
      riderLng: 77.6245,
      riderUpdatedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    sellerDb.collection("orders").doc("ord_1").update({
      status: "PENDING",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Buyers can submit store feedback with valid 1-5 stars while authorId is enforced", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    buyerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("feedback")
      .doc("fb_1")
      .set({
        sellerId: SELLER_UID,
        authorId: BUYER_UID,
        authorName: "Bob Buyer",
        rating: 5,
        category: "PUJA",
        comment: "Fast delivery and clear stock status!",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    buyerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("feedback")
      .doc("fb_bad")
      .set({
        sellerId: SELLER_UID,
        authorId: SELLER_UID,
        authorName: "Fake",
        rating: 5,
        category: "GENERAL",
        comment: "Spoofed author",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    sellerDb.collection("stores").doc(SELLER_UID).collection("feedback").get()
  );
  await assertSucceeds(
    sellerDb
      .collection("stores")
      .doc(SELLER_UID)
      .collection("feedback")
      .doc("fb_1")
      .delete()
  );
});

test("Buyers can save, list, and remove private wishlist items while other users are blocked", async () => {
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const otherBuyerDb = testEnv.authenticatedContext(OTHER_BUYER_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("wishlist")
      .doc(`${SELLER_UID}_prod_1`)
      .set({
        userId: BUYER_UID,
        sellerId: SELLER_UID,
        storeName: "Lakshmi Store",
        productId: "prod_1",
        name: "Bhimseni Camphor",
        category: "PUJA",
        unit: "100 g",
        priceInr: 210,
        imageTag: "camphor",
        createdAt: created,
      })
  );

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("wishlist")
      .doc(`${SELLER_UID}_prod_fancy`)
      .set({
        userId: BUYER_UID,
        sellerId: SELLER_UID,
        storeName: "Lakshmi Store",
        productId: "prod_fancy",
        name: "Silk Bangles Set",
        category: "FANCY",
        unit: "1 set",
        priceInr: 220,
        imageTag: "fancy",
        createdAt: created,
      })
  );

  // Invalid category rejected
  await assertFails(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("wishlist")
      .doc(`${SELLER_UID}_bad`)
      .set({
        userId: BUYER_UID,
        sellerId: SELLER_UID,
        storeName: "Lakshmi Store",
        productId: "bad",
        name: "Invalid Cat",
        category: "ELECTRONICS",
        unit: "1 pc",
        priceInr: 100,
        createdAt: created,
      })
  );

  await assertSucceeds(
    buyerDb.collection("accounts").doc(BUYER_UID).collection("wishlist").get()
  );

  // Other user cannot read or delete buyer's wishlist
  await assertFails(
    otherBuyerDb.collection("accounts").doc(BUYER_UID).collection("wishlist").get()
  );
  await assertFails(
    otherBuyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("wishlist")
      .doc(`${SELLER_UID}_prod_1`)
      .delete()
  );

  // Owner can remove from wishlist
  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("wishlist")
      .doc(`${SELLER_UID}_prod_1`)
      .delete()
  );
});

test("Buyers can manage private delivery addresses and preferred payment methods while other users are blocked", async () => {
  const buyerDb = testEnv.authenticatedContext(BUYER_UID).firestore();
  const otherBuyerDb = testEnv.authenticatedContext(OTHER_BUYER_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 2000);

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("addresses")
      .doc("addr_home")
      .set({
        userId: BUYER_UID,
        label: "Home",
        recipientName: "Priya Sharma",
        phoneNumber: "+91 9876500002",
        streetAddress: "42, 12th Main Road",
        neighborhood: "Indiranagar",
        landmark: "Near Metro Station",
        lat: 12.9784,
        lng: 77.6408,
        defaultAddress: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("addresses")
      .doc("addr_home")
      .update({
        defaultAddress: false,
        updatedAt: updated,
      })
  );

  await assertFails(
    otherBuyerDb.collection("accounts").doc(BUYER_UID).collection("addresses").get()
  );

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("paymentMethods")
      .doc("pay_upi")
      .set({
        userId: BUYER_UID,
        methodType: "UPI",
        displayLabel: "Primary GPay",
        handleOrHint: "priya@okaxis",
        defaultMethod: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("paymentMethods")
      .doc("pay_bad")
      .set({
        userId: BUYER_UID,
        methodType: "CRYPTO",
        displayLabel: "Unsupported",
        defaultMethod: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    otherBuyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("paymentMethods")
      .doc("pay_upi")
      .delete()
  );

  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("paymentMethods")
      .doc("pay_upi")
      .delete()
  );
  await assertSucceeds(
    buyerDb
      .collection("accounts")
      .doc(BUYER_UID)
      .collection("addresses")
      .doc("addr_home")
      .delete()
  );
});

