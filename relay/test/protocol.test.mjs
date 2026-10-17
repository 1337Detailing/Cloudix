import test from "node:test";
import assert from "node:assert/strict";
import {uuidBytes,bytesUuid,wrapFrame,RoomRelay} from "../src/index.js";

test("binary framing is lossless and tagged with a UUID",()=>{
  const id="123e4567-e89b-42d3-a456-426614174000";
  const tlsRecord=Uint8Array.from([22,3,3,0,3,1,2,3]);
  const frame=wrapFrame(id,tlsRecord);
  assert.equal(bytesUuid(frame.subarray(0,16)),id);
  assert.deepEqual(frame.subarray(16),tlsRecord);
});
test("reject malformed IDs and frames larger than 64 KB",()=>{
  assert.throws(()=>uuidBytes("../escape"));
  assert.throws(()=>wrapFrame(crypto.randomUUID(),new Uint8Array(65537)));
});
test("client cannot inject JSON control messages into the server",async()=>{
  const sent=[];
  const ws={
    deserializeAttachment:()=>({role:"client",id:"123e4567-e89b-42d3-a456-426614174000"}),
    close:(code,why)=>sent.push({code,why})
  };
  const relay=new RoomRelay({getWebSockets:()=>[]},{});
  await relay.webSocketMessage(ws,JSON.stringify({type:"close",id:"x"}));
  assert.equal(sent[0].code,1003);
});
