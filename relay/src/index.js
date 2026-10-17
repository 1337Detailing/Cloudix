// Cloudix blind WebSocket relay. It only sees TLS records, never file contents.
// Deploy to YOUR Cloudflare account; no built-in third-party relay endpoint.
const MAX_FRAME = 65_552; // UUID (16) + max TLS chunk (65,536)
const MAX_CLIENTS = 16;

export function uuidBytes(id) {
  if (!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(id)) throw Error("bad UUID");
  const h = id.replace(/-/g, "");
  return Uint8Array.from({length:16}, (_,i)=>parseInt(h.slice(i*2,i*2+2),16));
}
export function bytesUuid(bytes) {
  if (bytes.length !== 16) throw Error("bad UUID bytes");
  const h = [...bytes].map(x=>x.toString(16).padStart(2,"0")).join("");
  return h.slice(0,8)+"-"+h.slice(8,12)+"-"+h.slice(12,16)+"-"+h.slice(16,20)+"-"+h.slice(20);
}
export function wrapFrame(id, data) {
  const input = new Uint8Array(data);
  if(input.length>65_536) throw Error("frame too large");
  const result = new Uint8Array(16+input.length);
  result.set(uuidBytes(id),0);
  result.set(input,16);
  return result;
}
function safeSend(ws, payload) {
  try { ws.send(payload); return true; } catch { try { ws.close(1011,"Delivery failed"); } catch {} return false; }
}
function secureEqual(a,b) {
  if(typeof a!=="string" || typeof b!=="string" || a.length!==b.length) return false;
  let diff=0;
  for(let i=0;i<a.length;i++) diff|=a.charCodeAt(i)^b.charCodeAt(i);
  return diff===0;
}
async function hexDigest(s) {
  const digest=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(s));
  return [...new Uint8Array(digest)].map(v=>v.toString(16).padStart(2,"0")).join("");
}

export default {
  fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") {
      return Response.json({service:"Cloudix relay",version:1,filesStored:false});
    }
    if (url.pathname !== "/ws" || request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Not found", {status:404});
    }
    const room=url.searchParams.get("room");
    if(!room || !/^[a-f0-9]{40}$/.test(room)) return new Response("Invalid room", {status:400});
    const id=env.ROOMS.idFromName(room);
    return env.ROOMS.get(id).fetch(request);
  }
};

export class RoomRelay {
  constructor(ctx, env) { this.ctx=ctx; this.env=env; }
  members(role) { return this.ctx.getWebSockets(role); }
  async fetch(request) {
    const url=new URL(request.url);
    const role=url.searchParams.get("role");
    const bearer=request.headers.get("Authorization") || "";
    if((role!=="server" && role!=="client") || !/^Bearer [a-f0-9]{64}$/.test(bearer)) {
      return new Response("Unauthorized", {status:401});
    }
    // Only an authenticated server may create a room.
    const hashed=await hexDigest(bearer.slice(7));
    const known=await this.ctx.storage.get("token_sha256");
    if(known) {
      if(!secureEqual(known,hashed)) return new Response("Unauthorized", {status:401});
    } else {
      if(role!=="server") return new Response("Server offline", {status:503});
      await this.ctx.storage.put("token_sha256",hashed);
    }
    if (role==="client" && !this.members("server").length)
      return new Response("Server offline", {status:503});
    if (role==="client" && this.members("client").length>=MAX_CLIENTS)
      return new Response("Busy", {status:429});

    const pair=new WebSocketPair();
    const [client,server]=Object.values(pair);
    const sid=role==="client"?crypto.randomUUID():"";
    this.ctx.acceptWebSocket(server,[role]);
    server.serializeAttachment({role,id:sid});
    if(role==="server") {
      // Only one server per room; invalidate prior sessions.
      for(const prev of this.members("server")) {
        if(prev!==server) { try { prev.close(4001,"Replaced by server"); } catch {} }
      }
      for(const c of this.members("client")) { try { c.close(1012,"Server restarted"); } catch {} }
    } else {
      const primary=this.members("server")[0];
      if(primary) safeSend(primary,JSON.stringify({type:"open",id:sid}));
    }
    return new Response(null,{status:101,webSocket:client});
  }
  async webSocketMessage(ws,message) {
    const state=ws.deserializeAttachment();
    if(!state) return;
    if(state.role==="client") {
      if(!(message instanceof ArrayBuffer)) { ws.close(1003,"Binary only"); return; }
      const data=new Uint8Array(message);
      if(data.length>65_536) { ws.close(1009,"Frame too large"); return; }
      const server=this.members("server")[0];
      if(!server) { ws.close(1012,"Server offline"); return; }
      safeSend(server,wrapFrame(state.id,data));
    } else if(state.role==="server") {
      if(typeof message==="string") {
        let data;
        try { data=JSON.parse(message); } catch { ws.close(1003,"Invalid control frame");return; }
        if(data.type!=="close" || typeof data.id!=="string") {
          ws.close(1003,"Invalid control frame"); return;
        }
        for(const c of this.members("client")) {
          if(c.deserializeAttachment()?.id===data.id) {
            try {c.close(1000,"Session ended");} catch{}
            break;
          }
        }
        return;
      }
      if(!(message instanceof ArrayBuffer)) return;
      const b=new Uint8Array(message);
      if(b.length<16 || b.length>MAX_FRAME) { ws.close(1009,"Frame invalid");return; }
      const id=bytesUuid(b.subarray(0,16));
      for(const c of this.members("client")) {
        if(c.deserializeAttachment()?.id===id) {
          safeSend(c,b.subarray(16).buffer);
          break;
        }
      }
    }
  }
  async webSocketClose(ws) {
    const state=ws.deserializeAttachment();
    if(!state) return;
    if(state.role==="client") {
      for(const s of this.members("server")) {
        safeSend(s,JSON.stringify({type:"close",id:state.id}));
      }
    } else if(state.role==="server") {
      for(const c of this.members("client")) {
        try { c.close(1012,"Server offline"); } catch {}
      }
    }
    try {ws.close(1000,"Closed");}catch{}
  }
  async webSocketError(ws) {
    await this.webSocketClose(ws);
  }
}
