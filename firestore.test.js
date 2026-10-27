const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";

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

function validEventPayload(userId, now) {
  return {
    userId,
    title: "Giỗ Tổ Hùng Vương",
    notes: "Nghỉ lễ 10/3 Âm lịch",
    solarDay: 26,
    solarMonth: 4,
    solarYear: 2026,
    lunarDay: 10,
    lunarMonth: 3,
    lunarYear: 2026,
    isLunarLeapMonth: false,
    isLunarRecurring: true,
    category: "holiday",
    reminderMinutesBefore: 60,
    syncedToGoogle: false,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write events", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("events").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_1")
      .set(validEventPayload(ALICE_UID, new Date()))
  );
});

test("Authenticated owner can create, read, update, and delete their own event", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("events")
    .doc("evt_1");

  await assertSucceeds(docRef.set(validEventPayload(ALICE_UID, now)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    docRef.update({
      title: "Giỗ Tổ Hùng Vương (Cập nhật)",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user access to events is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_alice")
      .set(validEventPayload(ALICE_UID, now))
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("events")
      .doc("evt_alice")
      .get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("events")
    .doc("evt_shadow");

  await assertSucceeds(docRef.set(validEventPayload(ALICE_UID, now)));
  await assertFails(
    docRef.update({
      title: "Hack",
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Shared schedule enforces email_verified for recipient access and blocks unverified spoof", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: ALICE_EMAIL,
      email_verified: true,
    })
    .firestore();
  const now = new Date(Date.now() - 1000);
  const shareRef = aliceDb.collection("shared_schedules").doc("share_1");

  await assertSucceeds(
    shareRef.set({
      ownerId: ALICE_UID,
      ownerName: "Alice",
      recipientEmail: BOB_EMAIL,
      title: "Họp mặt cuối năm",
      notes: "Tất niên Âm lịch",
      solarDateIso: "2027-01-30",
      lunarDateDisplay: "23/12 Bính Ngọ",
      category: "personal",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Verified Bob can read
  const bobVerifiedDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: BOB_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    bobVerifiedDb.collection("shared_schedules").doc("share_1").get()
  );

  // Unverified spoofed Bob cannot read
  const bobSpoofDb = testEnv
    .authenticatedContext("spoof_999", {
      email: BOB_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(
    bobSpoofDb.collection("shared_schedules").doc("share_1").get()
  );
});
