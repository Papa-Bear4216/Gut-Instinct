import assert from "node:assert/strict";
import test from "node:test";
import { handleGutDelegate } from "./delegate.mjs";

const envelope = {
  v: 1,
  id: "0123456789abcdef",
  from: "pieces-android-companion-seamless",
  to: "gut-instinct",
  capability: "coach",
  payload: {},
};

test("contract reads the coach evidence floor and denylist size", () => {
  const outcome = handleGutDelegate({ ...envelope, action: "contract" });
  assert.equal(outcome.status, 200);
  assert.equal(outcome.body.result.minEvidence, 3);
  assert.ok(outcome.body.result.sensitivePackages > 0);
  assert.equal(outcome.body.result.packages, undefined);
});
