/* Локальный мост: страница общается не с ПК, а с opencode serve на этом же телефоне.
   Протокол сообщений повторяет oc_server.py (state/upd/chats/models/fs/asks/...). */
(function () {
  "use strict";

  const API = "/oc";
  const TOKEN = (() => {
    const m = location.hash.match(/(?:^#|&)token=([^&]+)/);
    return m ? decodeURIComponent(m[1]) : "";
  })();
  const BR = (window.Remote = window.Android || {
    notify() {}, vibrate() {}, copyText() {}, openUrl() {},
    getSettings() { return "{}"; }, saveSetting() {}, openSettings() {}, log() {}
  });

  const BS = {
    sid: null, chat: {}, chats: [], msgs: new Map(), order: [],
    active: new Set(), done: {}, asks: [], attachments: [],
    hasOlder: false, olderCursor: null, usage: { cost: 0, tokens: null },
    models: [], providers: [], online: false, status: "запуск…",
    execDone: true, lastRaw: null, sent: new Map(), sentChats: "",
    sentAsks: "", sentAtts: "", busy: false, home: "/sdcard/OpenCode",
    version: "", started: false, timers: false, pendingPush: 0,
    pushTail: false, asksPending: false, sessionLock: false,
    events: 0, lastEvent: "", lastTail: null, lastErr: ""
  };

  function handle(m) { if (typeof window.handle === "function") window.handle(m); }

  const DEFAULT_MODEL = { id: "deepseek-v4.1-flash-free", providerID: "opencode" };
  function savedModel() {
    try {
      const s = JSON.parse(localStorage.getItem("oc_model") || "null");
      if (s && s.id && s.providerID) return s;
    } catch (e) {}
    return DEFAULT_MODEL;
  }
  function toast(text, kind) { handle({ t: "toast", text: String(text || ""), kind: kind || "ok" }); }
  function log(msg) { try { BR.log("[bridge] " + msg); } catch (e) {} }

  async function json(method, path, body, timeout) {
    const ctl = new AbortController();
    const tm = setTimeout(() => ctl.abort(), timeout || 60000);
    try {
      const opt = { method, signal: ctl.signal,
        headers: { "Accept": "application/json", "X-Bridge-Token": TOKEN } };
      if (body !== undefined && body !== null) {
        opt.headers["Content-Type"] = "application/json";
        opt.body = JSON.stringify(body);
      }
      const r = await fetch(API + path, opt);
      const txt = await r.text();
      let data = null;
      try { data = txt ? JSON.parse(txt) : null; } catch (e) { data = null; }
      if (r.status >= 400) {
        const msg = (data && (data.message || data.error)) || txt.slice(0, 300) || ("HTTP " + r.status);
        throw new Error(msg);
      }
      return data;
    } finally {
      clearTimeout(tm);
    }
  }

  async function android(path) {
    const r = await fetch("/android/" + path, { headers: { "X-Bridge-Token": TOKEN } });
    return await r.json();
  }

  /* POST/PATCH к локальному серверу приложения (не к OpenCode). */
  async function androidReq(method, path, body) {
    const opt = { method, headers: { "Accept": "application/json", "X-Bridge-Token": TOKEN } };
    if (body !== undefined && body !== null) {
      opt.headers["Content-Type"] = "application/json";
      opt.body = JSON.stringify(body);
    }
    const r = await fetch("/android/" + path, opt);
    const txt = await r.text();
    let data = null;
    try { data = txt ? JSON.parse(txt) : null; } catch (e) {}
    if (r.status >= 400 || !data || data.ok === false) {
      throw new Error((data && (data.error || data.message)) || txt.slice(0, 200) || ("HTTP " + r.status));
    }
    return data;
  }

  /* ---------------------------------------------------------- сообщения --- */

  function toolHint(inp) {
    if (typeof inp === "string") return inp.replace(/\s+/g, " ").trim().slice(0, 120);
    if (inp && typeof inp === "object") {
      for (const k of ["description", "command", "filePath", "path", "pattern", "query", "url", "task", "prompt"]) {
        const v = inp[k];
        if (typeof v === "string" && v.trim()) return v.replace(/\s+/g, " ").trim().slice(0, 120);
      }
      try { return JSON.stringify(inp).slice(0, 120); } catch (e) {}
    }
    return "";
  }

  function renderMessage(m) {
    if (!m || typeof m !== "object") return null;
    const t = m.type, mid = m.id, ts = (m.time || {}).created;
    if (t === "user") {
      const files = [];
      for (const f of (m.files || [])) {
        if (f && typeof f === "object") files.push({ name: f.name || f.uri || "файл", uri: f.uri || "" });
        else if (typeof f === "string") files.push({ name: f.split(/[\\/]/).pop(), uri: f });
      }
      return { id: mid, role: "user", text: m.text || (m.metadata || {}).displayText || "", files, ts };
    }
    if (t === "assistant") {
      const blocks = [];
      for (const p of (m.content || [])) {
        if (!p || typeof p !== "object") continue;
        if (p.type === "text") {
          if (p.text) blocks.push({ k: "text", text: p.text });
        } else if (p.type === "reasoning") {
          if (p.text) blocks.push({ k: "reason", text: p.text, done: !!(p.time && p.time.completed) });
        } else if (p.type === "tool") {
          const st = p.state || {};
          let out = "";
          for (const c of (st.content || [])) {
            if (c && typeof c === "object" && c.type === "text") out += c.text || "";
            else if (typeof c === "string") out += c;
          }
          if (out.length > 4000) out = out.slice(0, 4000) + "\n… (обрезано)";
          blocks.push({
            k: "tool", name: p.name || "tool", status: st.status || "",
            hint: toolHint(st.input), input: st.input, out
          });
        } else if (p.type) {
          blocks.push({ k: "info", text: (p.type + ": " + String(p.text || "")).slice(0, 200) });
        }
      }
      const model = m.model || {};
      let finish = m.finish;
      if (finish && typeof finish === "object") finish = finish.type;
      const err = m.error || (m.metadata || {}).error;
      return {
        id: mid, role: "assistant", blocks, ts,
        model: model.modelID || model.id || "",
        finish: finish || "", cost: m.cost || 0, tokens: m.tokens || null,
        error: err ? String(err) : ""
      };
    }
    if (t === "shell") {
      return { id: mid, role: "info", text: "$ " + String(m.command || m.text || ""), ts };
    }
    const labels = {
      compaction: "Сжатие контекста", system: "Система", synthetic: "Служебное",
      skill: "Навык", "agent-switched": "Смена агента", "model-switched": "Смена модели",
      "location-switched": "Смена папки"
    };
    if (labels[t]) {
      const txt = m.text || m.title || "";
      return { id: mid, role: "info", text: labels[t] + (txt ? ": " + txt : ""), ts };
    }
    return null;
  }

  function msgDone(m) {
    if (!m || typeof m !== "object") return true;
    const t = m.type || "";
    if (t === "idle") return true;
    if (t !== "assistant") return false;
    if (!((m.time || {}).completed)) return false;
    for (const p of (m.content || [])) {
      if (!p || typeof p !== "object") continue;
      if (p.state && typeof p.state === "object" &&
          (p.state.status === "running" || p.state.status === "streaming")) return false;
      if (p.status === "running" || p.status === "streaming") return false;
    }
    return true;
  }

  function messageList() {
    const out = [];
    for (const id of BS.order) {
      const m = BS.msgs.get(id);
      if (m) out.push(m);
    }
    return out;
  }

  function chatDirectory() {
    return (BS.chat.location && BS.chat.location.directory) || BS.chat.directory || BS.home || "";
  }

  function chatBrief(s) {
    const loc = s.location || {};
    const tm = s.time || {};
    return {
      id: s.id,
      title: s.title || "Новый чат",
      directory: loc.directory || "",
      updated: tm.updated || tm.created || 0,
      ago: ago(tm.updated || tm.created),
      busy: BS.active.has(s.id),
      done: BS.done[s.id] || 0,
      cost: s.cost || 0,
      parent: s.parentID
    };
  }

  function ago(ms) {
    if (!ms) return "";
    const d = Math.max(0, Date.now() - ms);
    if (d < 60000) return "сейчас";
    if (d < 3600000) return Math.floor(d / 60000) + " мин";
    if (d < 86400000) return Math.floor(d / 3600000) + " ч";
    return Math.floor(d / 86400000) + " дн";
  }

  function state() {
    return {
      t: "state", ver: 75, online: BS.online, status: BS.status,
      oc_version: BS.version, busy: busy(),
      chat: {
        id: BS.sid, title: BS.chat.title || "", directory: chatDirectory(),
        model: BS.chat.model || {}, agent: BS.chat.agent || ""
      },
      chats: BS.chats, messages: messageList(), asks: BS.asks,
      attachments: BS.attachments, hasOlder: BS.hasOlder,
      usage: BS.usage,
      media: [BS.home], home: BS.home,
      server: { ip: "127.0.0.1", port: 8769 }
    };
  }

  function applyState() {
    BS.sent = new Map();
    for (const m of messageList()) if (m.id) BS.sent.set(m.id, JSON.stringify(m));
    BS.sentChats = JSON.stringify(BS.chats);
    BS.sentAsks = JSON.stringify(BS.asks);
    BS.sentAtts = JSON.stringify(BS.attachments);
    handle(state());
  }

  function busy() { return !!(BS.sid && BS.active.has(BS.sid) && !BS.execDone); }

  function push() {
    const changed = [];
    for (const m of messageList()) {
      if (!m.id) continue;
      const js = JSON.stringify(m);
      if (BS.sent.get(m.id) !== js) { BS.sent.set(m.id, js); changed.push(m); }
    }
    const chats = JSON.stringify(BS.chats);
    const asks = JSON.stringify(BS.asks);
    const atts = JSON.stringify(BS.attachments);
    handle({
      t: "upd", busy: busy(), messages: changed,
      chat: {
        id: BS.sid, title: BS.chat.title || "", directory: chatDirectory(),
        model: BS.chat.model || {}, agent: BS.chat.agent || ""
      },
      usage: BS.usage, online: BS.online, status: BS.status,
      chats: BS.chats,
      asks: asks !== BS.sentAsks ? BS.asks : null,
      attachments: atts !== BS.sentAtts ? BS.attachments : null
    });
    BS.sentChats = chats; BS.sentAsks = asks; BS.sentAtts = atts;
  }

  function schedulePush(delay, tail) {
    if (tail) BS.pushTail = true;
    if (BS.pendingPush) return;
    BS.pendingPush = setTimeout(async () => {
      BS.pendingPush = 0;
      const doTail = BS.pushTail;
      BS.pushTail = false;
      try {
        if (doTail) await tailRefresh();
        push();
      } catch (e) { BS.lastErr = "push: " + e.message; log(BS.lastErr); }
    }, delay || 150);
  }

  /* ------------------------------------------------------------- запросы --- */

  async function refreshChats() {
    const r = await json("GET", "/api/session?limit=80&order=desc", null, 30000);
    const arr = (r && r.data) || [];
    BS.chats = arr.filter(s => !s.parentID).map(chatBrief);
    return BS.chats;
  }

  async function refreshActive() {
    try {
      const r = await json("GET", "/api/session/active", null, 10000);
      const d = (r && r.data) || {};
      const next = new Set(Object.keys(d || {}));
      for (const sid of BS.active) if (!next.has(sid)) BS.done[sid] = Date.now() / 1000;
      BS.active = next;
    } catch (e) { /* не критично */ }
  }

  async function refreshAsks() {
    if (!BS.sid) { BS.asks = []; return; }
    const out = [];
    try {
      const p = await json("GET", "/api/session/" + BS.sid + "/permission", null, 10000);
      for (const it of ((p && p.data) || [])) {
        out.push({
          kind: "perm", id: it.id || "", action: it.action || "",
          resources: it.resources || [], message: it.message || "", save: it.save || []
        });
      }
    } catch (e) {}
    try {
      const f = await json("GET", "/api/session/" + BS.sid + "/form", null, 10000);
      for (const it of ((f && f.data) || [])) {
        const st = (it.state || {}).status || "pending";
        if (st !== "pending") continue;
        out.push({ kind: "form", id: it.id || "", title: it.title || "Вопрос", fields: it.fields || [] });
      }
    } catch (e) {}
    BS.asks = out;
  }

  function scheduleAsks(delay) {
    if (BS.asksPending) return;
    BS.asksPending = setTimeout(async () => {
      BS.asksPending = false;
      try { await refreshAsks(); push(); } catch (e) {}
    }, delay || 300);
  }

  async function fetchMessages(sid, limit, cursor) {
    let p = "/api/session/" + sid + "/message?order=desc&limit=" + (limit || 200);
    if (cursor) p += "&cursor=" + encodeURIComponent(cursor);
    return await json("GET", p, null, 60000);
  }

  async function openSession(sid, silent) {
    if (!sid || BS.sessionLock) return;
    BS.sessionLock = true;
    try {
      BS.sid = sid;
      BS.msgs = new Map(); BS.order = [];
      BS.sent = new Map(); BS.attachments = [];
      BS.hasOlder = false; BS.olderCursor = null; BS.execDone = true; BS.lastRaw = null;
      try {
        const s = await json("GET", "/api/session/" + sid, null, 30000);
        BS.chat = (s && s.data) || { id: sid };
      } catch (e) { BS.chat = { id: sid }; }
      if (!BS.chat.model || !BS.chat.model.id) {
        const m = savedModel();
        try {
          await json("POST", "/api/session/" + sid + "/model", { model: m }, 20000);
          BS.chat.model = m;
        } catch (e) {}
      }
      try { await _fetch(sid, 200, null); } catch (e) { toast("Не удалось загрузить чат: " + e.message, "err"); }
      await refreshActive();
      await refreshAsks();
      try { localStorage.setItem("oc_last_session", sid); } catch (e) {}
      applyState();
      refreshChats().then(push).catch(() => {});
    } finally {
      BS.sessionLock = false;
    }
  }

  async function _fetch(sid, limit, cursor) {
    const r = await fetchMessages(sid, limit, cursor);
    if (BS.sid !== sid) return;
    const items = ((r && r.data) || []).slice().reverse();
    BS.olderCursor = (r.cursor || {}).previous || null;
    BS.hasOlder = !!BS.olderCursor;
    if (items.length) { BS.lastRaw = items[items.length - 1]; BS.execDone = msgDone(BS.lastRaw); }
    for (const raw of items) {
      const m = renderMessage(raw);
      if (!m || !m.id) continue;
      if (!BS.msgs.has(m.id)) BS.order.push(m.id);
      BS.msgs.set(m.id, m);
    }
  }

  async function loadOlder() {
    if (!BS.sid || !BS.olderCursor) return;
    const sid = BS.sid;
    const r = await fetchMessages(sid, 100, BS.olderCursor);
    if (BS.sid !== sid) return;
    const items = ((r && r.data) || []).slice().reverse();
    const newIds = [];
    for (const raw of items) {
      const m = renderMessage(raw);
      if (!m || !m.id) continue;
      if (!BS.msgs.has(m.id)) newIds.push(m.id);
      BS.msgs.set(m.id, m);
    }
    BS.order = newIds.concat(BS.order);
    BS.olderCursor = (r.cursor || {}).previous || null;
    BS.hasOlder = !!BS.olderCursor;
    applyState();
  }

  async function tailRefresh() {
    if (!BS.sid) return;
    const sid = BS.sid;
    const r = await fetchMessages(sid, 10, null);
    BS.lastTail = { items: (r && r.data ? r.data.length : -1), sid, cur: BS.sid, keys: r ? Object.keys(r) : null };
    if (BS.sid !== sid) return;
    const tail = ((r && r.data) || []).slice().reverse();
    if (tail.length) { BS.lastRaw = tail[tail.length - 1]; BS.execDone = msgDone(BS.lastRaw); }
    for (const raw of tail) {
      const m = renderMessage(raw);
      if (!m || !m.id) continue;
      if (!BS.msgs.has(m.id)) BS.order.push(m.id);
      BS.msgs.set(m.id, m);
    }
    if (BS.order.length > 400) {
      for (const id of BS.order.slice(0, BS.order.length - 400)) {
        BS.msgs.delete(id); BS.sent.delete(id);
      }
      BS.order = BS.order.slice(-400);
    }
  }

  async function syncMeta() {
    if (!BS.sid) return;
    try {
      const s = await json("GET", "/api/session/" + BS.sid, null, 20000);
      if (s && s.data) BS.chat = s.data;
    } catch (e) {}
  }

  async function createSession(dir) {
    const directory = dir || BS.home;
    try {
      const r = await json("POST", "/api/session", { location: { directory } }, 60000);
      const sid = (r && r.data && r.data.id) || null;
      if (!sid) throw new Error("нет id сессии");
      await refreshChats();
      handle({ t: "chats", items: BS.chats });
      await openSession(sid);
      toast("Новый чат: " + directory);
      return sid;
    } catch (e) {
      toast("Не удалось создать чат: " + e.message, "err");
      return null;
    }
  }

  /* -------------------------------------------------------------- команды --- */

  async function send(o) {
    if (!o || !o.t) return;
    try {
      switch (o.t) {
        case "init":
          await refreshAll(true);
          break;
        case "refresh":
          await refreshAll(false);
          break;
        case "open":
          await openSession(o.id);
          break;
        case "new":
          await createSession(o.directory || BS.home);
          break;
        case "chats":
          await refreshChats();
          handle({ t: "chats", items: BS.chats });
          break;
        case "send":
          await sendPrompt(o.text);
          break;
        case "stop":
          await stopPrompt();
          break;
        case "rename":
          await rename(o.id, o.title);
          break;
        case "delchat":
          await delChat(o.id);
          break;
        case "move":
          await move(o.directory);
          break;
        case "models":
          await listModels();
          break;
        case "setmodel":
          await setModel(o.id, o.provider, o.variant);
          break;
        case "attach":
          await attach(o.path, o.kind);
          break;
        case "detach":
          BS.attachments.splice(o.index, 1);
          push();
          break;
        case "fs":
          await fs(o.path);
          break;
        case "mkdir":
          await mkdir(o.parent, o.name);
          break;
        case "older":
          await loadOlder();
          break;
        case "ansperm":
          await answerPerm(o.id, o.decision);
          break;
        case "ansform":
          await answerForm(o.id, o.answer);
          break;
        case "providers":
          await listProviders();
          break;
        case "login":
          await loginStart();
          break;
        case "logincode":
          await loginCode(o.code);
          break;
        case "logout":
          await logoutAccount();
          break;
        case "zenkey":
          await saveZenKey(o.key);
          break;
        case "catalog":
          await loadCatalog(!!o.force);
          break;
        case "connectkey":
          await connectProviderKey(o.integration, o.key);
          break;
        case "connectoauth":
          await startOAuth(o.integration, o.methodID, o.answer);
          break;
        case "oauthcode":
          await oauthCode(o.code);
          break;
        case "disconnectprov":
          await disconnectProvider(o.integration, o.credential);
          break;
        case "provmodels":
          await providerModels(o.integration);
          break;
        case "websearch":
          await loadWebsearch();
          break;
        case "setwebsearch":
          await setWebsearch(o.provider);
          break;
        case "wskey":
          await websearchKey(o.integration, o.key);
          break;
        case "wscheck":
          await websearchCheck(o.integration);
          break;
        case "appupdate":
          loadAppUpdate(!!o.force);
          break;
        case "appupdateinstall":
          appUpdateInstall();
          break;
        case "usage":
          await loadUsage();
          break;
        case "ping":
          handle({ t: "pong" });
          break;
        case "debug":
          BR.log("PAGE: " + (o.msg || ""));
          break;
        default:
          log("неизвестная команда: " + o.t);
      }
    } catch (e) {
      toast(e.message || String(e), "err");
    }
  }

  async function sendPrompt(text) {
    if (!BS.sid) { toast("Сначала откройте или создайте чат", "err"); return; }
    const body = { text: String(text || "") };
    if (BS.attachments.length) {
      body.files = BS.attachments.map(a => ({ uri: a.uri || ("file://" + a.path), name: a.name }));
    }
    await json("POST", "/api/session/" + BS.sid + "/prompt", body, 90000);
    BS.execDone = false;
    BS.active.add(BS.sid);
    BS.attachments = [];
    handle({ t: "sent" });
    try { await tailRefresh(); } catch (e) {}
    push();
    schedulePush(400, true);
  }

  async function stopPrompt() {
    if (!BS.sid) return;
    await json("POST", "/api/session/" + BS.sid + "/interrupt", {}, 30000);
    BS.active.delete(BS.sid);
    BS.execDone = true;
    toast("Остановил работу");
    try { await tailRefresh(); } catch (e) {}
    push();
  }

  async function rename(id, title) {
    title = String(title || "").trim();
    if (!id || !title) { toast("Пустое название", "err"); return; }
    await json("PATCH", "/api/session/" + id, { title: title.slice(0, 120) }, 30000);
    for (const c of BS.chats) if (c.id === id) c.title = title;
    if (id === BS.sid) BS.chat.title = title;
    handle({ t: "chats", items: BS.chats });
    push();
    toast("Название: «" + title + "»");
  }

  async function delChat(id) {
    if (!id) return;
    await json("DELETE", "/api/session/" + id, null, 30000);
    BS.chats = BS.chats.filter(c => c.id !== id);
    delete BS.done[id];
    handle({ t: "chats", items: BS.chats });
    if (id === BS.sid) {
      if (BS.chats.length) await openSession(BS.chats[0].id);
      else {
        BS.sid = null; BS.msgs = new Map(); BS.order = []; BS.chat = {};
        applyState();
      }
    }
    toast("Чат удалён");
  }

  async function move(dir) {
    if (!BS.sid || !dir) return;
    await json("POST", "/api/session/" + BS.sid + "/move", { directory: dir }, 60000);
    if (!BS.chat.location) BS.chat.location = {};
    BS.chat.location.directory = dir;
    toast("Рабочая папка: " + dir);
    push();
  }

  async function listModels() {
    const r = await json("GET", "/api/model", null, 60000);
    const list = modelItems(r);
    BS.models = list;
    handle({ t: "models", items: list, current: BS.chat.model || {} });
  }

  /* то же, но без открытия листа моделей на странице */
  async function listModelsQuiet() {
    try {
      BS.models = modelItems(await json("GET", "/api/model", null, 60000));
    } catch (e) {}
  }

  function modelItems(r) {
    const items = [];
    for (const m of ((r && r.data) || [])) {
      if (m.enabled === false) continue;
      let free = true;
      for (const tier of (m.cost || [])) {
        if (tier && typeof tier === "object") {
          if (tier.input > 0 || tier.output > 0) free = false;
        }
      }
      items.push({
        id: m.id, modelID: m.modelID, providerID: m.providerID,
        name: m.name || m.id, family: m.family || "", status: m.status || "",
        variants: m.variants || [], free, limit: m.limit || {}
      });
    }
    // Одна и та же модель бывает и в opencode (оплата по факту), и в opencode-go
    // (подписка). Оставляем подписочную, чтобы не ловить 402 «Insufficient funds».
    const best = new Map();
    const rank = x => x.providerID === "opencode-go" ? 2 : (x.providerID === "opencode" ? 1 : 0);
    for (const it of items) {
      const cur = best.get(it.id);
      if (!cur || rank(it) > rank(cur)) best.set(it.id, it);
    }
    const list = Array.from(best.values());
    list.sort((a, b) => (a.free === b.free ? 0 : (a.free ? -1 : 1))
      || String(a.name).toLowerCase().localeCompare(String(b.name).toLowerCase()));
    return list;
  }

  async function setModel(id, provider, variant) {
    if (!BS.sid) return;
    const model = { id: id, providerID: provider };
    if (variant) model.variant = variant;
    await json("POST", "/api/session/" + BS.sid + "/model", { model }, 30000);
    BS.chat.model = model;
    try { localStorage.setItem("oc_model", JSON.stringify(model)); } catch (e) {}
    toast("Модель: " + id);
    push();
  }

  async function attach(path, kind) {
    if (!path) return;
    const name = String(path).split(/[\\/]/).pop() || "файл";
    BS.attachments.push({ path, name, kind: kind || "file", uri: "file://" + path });
    push();
  }

  async function fs(path) {
    const q = encodeURIComponent(path || BS.home || "");
    const r = await fetch("/android/fs?path=" + q, { headers: { "X-Bridge-Token": TOKEN } }).then(x => x.json());
    handle({
      t: "fs", path: r.path || path || "", parent: r.parent || null,
      drives: !!r.drives, entries: r.entries || [], shortcuts: r.shortcuts || [],
      error: r.ok === false ? (r.error || "нет доступа") : ""
    });
  }

  async function mkdir(parent, name) {
    const r = await fetch("/android/mkdir?parent=" + encodeURIComponent(parent || "")
      + "&name=" + encodeURIComponent(name || ""),
      { headers: { "X-Bridge-Token": TOKEN } }).then(x => x.json());
    if (!r.ok) { toast("Не удалось создать папку: " + (r.error || ""), "err"); return; }
    toast("Папка создана");
    await fs(parent);
  }

  async function answerPerm(id, decision) {
    if (!BS.sid || !id) return;
    if (!["once", "always", "reject"].includes(decision)) decision = "once";
    await json("POST", "/api/session/" + BS.sid + "/permission/" + id + "/reply",
      { decision }, 30000);
    BS.asks = BS.asks.filter(a => !(a.kind === "perm" && a.id === id));
    toast(decision === "reject" ? "Отклонено" : "Разрешение отправлено");
    push();
    scheduleAsks(300);
  }

  async function answerForm(id, answer) {
    if (!BS.sid || !id) return;
    await json("POST", "/api/session/" + BS.sid + "/form/" + id + "/reply",
      { answer: answer || {} }, 30000);
    BS.asks = BS.asks.filter(a => !(a.kind === "form" && a.id === id));
    toast("Ответ отправлен");
    push();
    scheduleAsks(300);
  }

  async function listProviders() {
    try {
      const r = await json("GET", "/api/provider", null, 30000);
      let list = BS.models;
      if (!list.length) {
        try {
          const mr = await json("GET", "/api/model", null, 60000);
          list = ((mr && mr.data) || []).map(m => ({
            id: m.id, providerID: m.providerID, name: m.name || m.id,
            free: !(m.cost || []).some(t => t && (t.input > 0 || t.output > 0))
          }));
        } catch (e) { list = []; }
      }
      BS.providers = ((r && r.data) || []).map(p => {
        const mine = list.filter(m => m.providerID === p.id);
        return {
          id: p.id, name: p.name || p.id,
          activation: p.activation || "enabled",
          hasKey: false,
          models: mine.length,
          freeModels: mine.filter(m => m.free).length,
          offModels: 0,
          sample: mine.slice(0, 6).map(m => m.name),
          canDelete: false
        };
      });
      // если аккаунт уже подключён — показываем это и после перезапуска
      try {
        const cr = await json("GET", "/api/credential", null, 15000);
        const cred = ((cr && cr.data) || []).find(c => c && c.integrationID === "opencode" && c.active !== false);
        if (cred) {
          const md = (cred.value && cred.value.metadata) || {};
          handle({ t: "login", status: "done", email: md.email || "", org: md.orgName || "" });
        }
      } catch (e) {}
      handle({ t: "providers", items: BS.providers });
    } catch (e) { handle({ t: "providers", items: [] }); }
  }

  async function loadUsage() {
    try {
      const r = await json("GET", "/api/experimental/usage/limits", null, 20000);
      // API отдаёт {location, data:{go, workspace, budgets}} — странице нужен data
      handle({ t: "usage", usage: (r && r.data) ? r.data : r, error: "" });
    } catch (e) {
      handle({ t: "usage", usage: null, error: e.message });
    }
  }

  async function saveZenKey(key) {
    const k = String(key || "").trim();
    if (!k) { toast("Введите ключ", "err"); return; }
    try {
      // удаляем только прежние КЛЮЧИ, не трогая вход по аккаунту (oauth)
      const list = await json("GET", "/api/credential", null, 20000);
      for (const c of ((list && list.data) || [])) {
        if (c && c.integrationID === "opencode" &&
            c.value && c.value.type === "key") {
          try { await json("DELETE", "/api/credential/" + c.id, null, 15000); } catch (e) {}
        }
      }
      await json("POST", "/api/credential", {
        integrationID: "opencode",
        label: "OpenCode Zen",
        activate: true,
        value: { type: "key", key: k }
      }, 25000);
      toast("Ключ OpenCode Zen сохранён");
      try { await listProviders(); } catch (e) {}
    } catch (e) {
      toast("Не удалось сохранить ключ: " + e.message, "err");
    }
  }

  /* --------------------------------------------------- вход по аккаунту --- */

  let loginState = null;

  function loginEvent(status, extra) {
    handle(Object.assign({ t: "login", status }, extra || {}));
  }

  async function loginStart() {
    if (loginState && loginState.polling) { toast("Вход уже выполняется…"); return; }
    loginEvent("starting");
    let r;
    try {
      r = await json("POST", "/api/integration/opencode/connect/oauth",
        { methodID: "device" }, 45000);
    } catch (e) {
      loginEvent("error", { error: e.message });
      toast("Не удалось начать вход: " + e.message, "err");
      return;
    }
    const d = (r && r.data) || r || {};
    const attemptID = d.attemptID;
    if (!attemptID) {
      loginEvent("error", { error: "нет attemptID" });
      toast("Сервер не выдал код входа", "err");
      return;
    }
    const mode = d.mode || "auto";
    const url = d.url || "";
    const instructions = d.instructions || "";
    loginState = { attemptID, mode, polling: true };
    loginEvent(mode === "code" ? "code" : "waiting", { url, instructions, mode });
    if (url) {
      try { BR.openUrl(url); } catch (e) { toast("Откройте ссылку: " + url); }
    } else {
      toast("Откройте ссылку из уведомления на сайте OpenCode");
    }
    if (mode === "code") {
      toast(instructions || "Введите код со страницы входа");
      return;
    }
    pollLogin();
  }

  async function loginCode(code) {
    if (!loginState || !loginState.polling) { toast("Сначала начните вход", "err"); return; }
    try {
      await json("POST", "/api/integration/opencode/connect/oauth/"
        + encodeURIComponent(loginState.attemptID) + "/complete",
        { code: code || null }, 30000);
    } catch (e) {
      toast("Код не принят: " + e.message, "err");
      return;
    }
    loginState.polling = false;
    await afterLogin();
  }

  async function pollLogin() {
    const deadline = Date.now() + 5 * 60 * 1000;
    while (loginState && loginState.polling && Date.now() < deadline) {
      await new Promise(r => setTimeout(r, 2500));
      let st;
      try {
        st = await json("GET", "/api/integration/opencode/connect/oauth/"
          + encodeURIComponent(loginState.attemptID), null, 20000);
      } catch (e) { continue; }
      const sd = (st && st.data) || st || {};
      const status = sd.status;
      if (status === "complete") {
        try {
          await json("POST", "/api/integration/opencode/connect/oauth/"
            + encodeURIComponent(loginState.attemptID) + "/complete",
            { code: null }, 30000);
        } catch (e) {}
        loginState.polling = false;
        await afterLogin();
        return;
      }
      if (status === "failed" || status === "expired") {
        loginState.polling = false;
        loginEvent("error", { error: sd.error || status });
        toast("Вход не выполнен: " + (sd.error || status), "err");
        return;
      }
    }
    if (loginState && loginState.polling) {
      loginState.polling = false;
      loginEvent("expired");
      toast("Время входа истекло", "err");
    }
  }

  async function afterLogin() {
    loginEvent("done");
    toast("Вход выполнен. Платные модели доступны.");
    try { await listProviders(); } catch (e) {}
    // тихо обновим список моделей, чтобы платные появились без перезапуска
    try {
      const r = await json("GET", "/api/model", null, 60000);
      const items = [];
      for (const m of ((r && r.data) || [])) {
        if (m.enabled === false) continue;
        let free = true;
        for (const tier of (m.cost || [])) {
          if (tier && typeof tier === "object" && (tier.input > 0 || tier.output > 0)) free = false;
        }
        items.push({
          id: m.id, modelID: m.modelID, providerID: m.providerID,
          name: m.name || m.id, family: m.family || "", status: m.status || "",
          variants: m.variants || [], free, limit: m.limit || {}
        });
      }
      BS.models = items;
    } catch (e) {}
  }

  async function logoutAccount() {
    try {
      const list = await json("GET", "/api/credential", null, 20000);
      const arr = (list && list.data) || [];
      for (const c of arr) {
        if (c && c.integrationID === "opencode") {
          try { await json("DELETE", "/api/credential/" + c.id, null, 20000); } catch (e) {}
        }
      }
      loginState = null;
      loginEvent("logout");
      toast("Аккаунт отключён");
      await listProviders();
    } catch (e) {
      toast("Не удалось выйти: " + e.message, "err");
    }
  }

  /* ------------------------------------ каталог провайдеров (integration) --- */

  const CAT_KEY = "oc_cat_v1";
  const CAT_TTL = 24 * 60 * 60 * 1000;
  let catalog = null;
  let oauthState = null;

  function normMethod(m) {
    return {
      id: m.id || "",
      type: m.type || "key",
      label: m.label || "",
      names: m.names || [],
      form: (m.form || []).map(f => ({
        key: f.key, type: f.type || "string", title: f.title || f.key,
        description: f.description || "", required: !!f.required,
        hidden: !!f.hidden,
        def: f.default === undefined ? "" : f.default,
        placeholder: f.placeholder || "",
        options: f.options || [], when: f.when || []
      }))
    };
  }

  async function loadCatalog(force) {
    if (!force && catalog) { handle({ t: "catalog", items: catalog }); return; }
    if (!force) {
      try {
        const raw = localStorage.getItem(CAT_KEY);
        if (raw) {
          const c = JSON.parse(raw);
          if (c && Array.isArray(c.items) && Date.now() - (c.at || 0) < CAT_TTL) {
            catalog = c.items;
            handle({ t: "catalog", items: catalog });
            return;
          }
        }
      } catch (e) {}
    }
    try {
      const r = await json("GET", "/api/integration", null, 60000);
      const arr = (r && r.data) || r || [];
      catalog = arr.map(it => ({
        id: it.id, name: it.name || it.id,
        methods: (it.methods || []).map(normMethod),
        connections: (it.connections || []).map(c => ({
          id: c.id, label: c.label || "", method: c.method || ""
        }))
      }));
      try {
        localStorage.setItem(CAT_KEY, JSON.stringify({ at: Date.now(), items: catalog }));
      } catch (e) {}
      handle({ t: "catalog", items: catalog });
    } catch (e) {
      handle({ t: "catalog", items: [], error: e.message });
    }
  }

  async function refreshProvidersAll() {
    try { await loadCatalog(true); } catch (e) {}
    try { await listProviders(); } catch (e) {}
    try { await listModelsQuiet(); } catch (e) {}
  }

  /* После подключения: если у провайдера нет моделей — предупредить. */
  async function afterProviderConnect(integration) {
    await refreshProvidersAll();
    const n = (BS.models || []).filter(m => m.providerID === integration).length;
    if (!n) {
      toast("Подключено, но моделей нет — проверьте подписку или ключ", "err");
    }
  }

  async function connectProviderKey(integration, key) {
    const k = String(key || "").trim();
    if (!k) { toast("Введите ключ", "err"); return; }
    handle({ t: "provbusy", integration, busy: true });
    try {
      await json("POST", "/api/integration/" + encodeURIComponent(integration) + "/connect/key",
        { key: k }, 60000);
      handle({ t: "provbusy", integration, busy: false });
      toast("Провайдер подключён");
      await afterProviderConnect(integration);
    } catch (e) {
      handle({ t: "provbusy", integration, busy: false });
      toast("Не удалось подключить: " + e.message, "err");
    }
  }

  function oauthEvent(integration, status, extra) {
    handle(Object.assign({ t: "oauth", integration, status }, extra || {}));
  }

  function oauthBase(integration) {
    return "/api/integration/" + encodeURIComponent(integration) + "/connect/oauth";
  }

  async function startOAuth(integration, methodID, answer) {
    if (oauthState && oauthState.polling) { toast("Подключение уже выполняется…"); return; }
    oauthEvent(integration, "starting");
    let r;
    try {
      r = await json("POST", oauthBase(integration),
        { methodID: methodID, answer: answer || null }, 60000);
    } catch (e) {
      oauthEvent(integration, "error", { error: e.message });
      toast("Не удалось начать вход: " + e.message, "err");
      return;
    }
    const d = (r && r.data) || r || {};
    const attemptID = d.attemptID;
    if (!attemptID) {
      oauthEvent(integration, "error", { error: "нет attemptID" });
      toast("Сервер не выдал код входа", "err");
      return;
    }
    oauthState = { integration, methodID, attemptID, mode: d.mode || "auto", polling: true };
    oauthEvent(integration, d.mode === "code" ? "code" : "waiting",
      { url: d.url || "", instructions: d.instructions || "", mode: oauthState.mode });
    if (d.url) {
      try { BR.openUrl(d.url); } catch (e) { toast("Откройте ссылку: " + d.url); }
    }
    if (oauthState.mode === "code") {
      toast(d.instructions || "Введите код со страницы входа");
      return;
    }
    pollOAuth();
  }

  async function oauthCode(code) {
    if (!oauthState || !oauthState.polling) { toast("Сначала начните вход", "err"); return; }
    const integration = oauthState.integration;
    try {
      await json("POST", oauthBase(integration) + "/"
        + encodeURIComponent(oauthState.attemptID) + "/complete",
        { code: code || null }, 30000);
    } catch (e) {
      toast("Код не принят: " + e.message, "err");
      return;
    }
    oauthState.polling = false;
    oauthEvent(integration, "done");
    toast("Провайдер подключён");
    await afterProviderConnect(integration);
  }

  async function pollOAuth() {
    const deadline = Date.now() + 5 * 60 * 1000;
    while (oauthState && oauthState.polling && Date.now() < deadline) {
      await new Promise(r => setTimeout(r, 2500));
      const integration = oauthState.integration;
      let st;
      try {
        st = await json("GET", oauthBase(integration) + "/"
          + encodeURIComponent(oauthState.attemptID), null, 20000);
      } catch (e) { continue; }
      const sd = (st && st.data) || st || {};
      const status = sd.status;
      if (status === "complete") {
        try {
          await json("POST", oauthBase(integration) + "/"
            + encodeURIComponent(oauthState.attemptID) + "/complete",
            { code: null }, 30000);
        } catch (e) {}
        oauthState.polling = false;
        oauthEvent(integration, "done");
        toast("Провайдер подключён");
        await afterProviderConnect(integration);
        return;
      }
      if (status === "failed" || status === "expired") {
        oauthState.polling = false;
        const msg = sd.message || sd.error || status;
        oauthEvent(integration, "error", { error: msg });
        toast("Вход не выполнен: " + msg, "err");
        return;
      }
    }
    if (oauthState && oauthState.polling) {
      oauthState.polling = false;
      oauthEvent(oauthState.integration, "expired");
      toast("Время входа истекло", "err");
    }
  }

  async function providerModels(integration) {
    try {
      const r = await json("GET", "/api/model", null, 60000);
      const items = ((r && r.data) || [])
        .filter(m => m.providerID === integration && m.enabled !== false)
        .map(m => ({
          id: m.id, modelID: m.modelID, name: m.name || m.id,
          free: !(m.cost || []).some(t => t && (t.input > 0 || t.output > 0))
        }));
      handle({ t: "provmodels", integration, items });
    } catch (e) {
      handle({ t: "provmodels", integration, items: [], error: e.message });
    }
  }

  async function disconnectProvider(integration, credentialID) {
    try {
      if (credentialID) {
        await json("DELETE", "/api/credential/" + encodeURIComponent(credentialID), null, 20000);
      } else {
        const l = await json("GET", "/api/credential", null, 20000);
        for (const c of ((l && l.data) || [])) {
          if (c && c.integrationID === integration) {
            try { await json("DELETE", "/api/credential/" + c.id, null, 20000); } catch (e) {}
          }
        }
      }
      toast("Провайдер отключён");
      await refreshProvidersAll();
    } catch (e) {
      toast("Не удалось отключить: " + e.message, "err");
    }
  }

  /* ------------------------------------------------ веб-поиск (провайдеры) --- */

  async function loadWebsearch() {
    try {
      const r = await json("GET", "/api/websearch/provider", null, 30000);
      const items = ((r && r.data) || []).map(p => ({ id: p.id, name: p.name || p.id, key: false }));
      let current = null, disabled = false;
      try {
        const cfg = await androidReq("GET", "occonfig");
        const ws = cfg && cfg.websearch;
        if (ws === false) disabled = true;
        else if (ws && ws.provider) current = ws.provider;
      } catch (e) {}
      try {
        const cr = await json("GET", "/api/credential", null, 15000);
        const creds = ((cr && cr.data) || []);
        for (const p of items) p.key = creds.some(c => c && c.integrationID === p.id && c.active !== false);
      } catch (e) {}
      BS.lastWebsearch = { items, current, disabled };
      handle({ t: "websearch", items, current, disabled, error: "" });
    } catch (e) {
      handle({ t: "websearch", items: [], current: null, disabled: false, error: e.message });
    }
  }

  async function setWebsearch(provider) {
    try {
      await androidReq("POST", "occonfig", { provider: provider || "" });
      await loadWebsearch();
      const ws = BS.lastWebsearch || {};
      const id = provider || "";
      const item = (ws.items || []).find(p => p.id === id);
      toast(id ? ("Веб-поиск: " + (item ? item.name : id)) : "Провайдер не закреплён — сервер выбирает сам");
    } catch (e) {
      toast("Не удалось сохранить выбор: " + e.message, "err");
    }
  }

  async function websearchKey(integration, key) {
    const k = String(key || "").trim();
    if (!k) { toast("Введите ключ", "err"); return; }
    try {
      await json("POST", "/api/integration/" + encodeURIComponent(integration) + "/connect/key",
                 { key: k }, 25000);
      toast("Ключ сохранён");
      await loadWebsearch();
    } catch (e) {
      toast("Не удалось сохранить ключ: " + e.message, "err");
    }
  }

  async function websearchCheck(integration) {
    handle({ t: "wscheck", integration, busy: true, items: [] });
    try {
      const r = await json("POST", "/api/websearch",
                           { query: "opencode ai coding agent", providerID: integration }, 120000);
      const d = (r && r.data) || {};
      handle({ t: "wscheck", integration, busy: false, ok: true,
               providerID: d.providerID || integration, items: d.results || [] });
    } catch (e) {
      handle({ t: "wscheck", integration, busy: false, ok: false, error: e.message, items: [] });
    }
  }

  /* ------------------------------------------- обновление приложения (GitHub) --- */

  function appUpdateState() {
    try {
      return JSON.parse(BR.appUpdateState ? BR.appUpdateState() : "{}") || {};
    } catch (e) {
      return { error: e.message };
    }
  }

  function emitAppUpdate(st) {
    handle(Object.assign({ t: "appupdate" }, st || {}));
  }

  function pollAppUpdate(n) {
    if (n > 30) return;
    setTimeout(() => {
      const st = appUpdateState();
      emitAppUpdate(st);
      if (st.checking) pollAppUpdate(n + 1);
    }, 1000);
  }

  function loadAppUpdate(force) {
    let st;
    try {
      st = JSON.parse(BR.appUpdateCheck ? BR.appUpdateCheck(!!force) : "{}") || {};
    } catch (e) {
      st = { error: e.message };
    }
    emitAppUpdate(st);
    if (force && !st.error) toast("Проверяю обновления…");
    pollAppUpdate(0);
  }

  function appUpdateInstall() {
    let st;
    try {
      st = JSON.parse(BR.appUpdateInstall ? BR.appUpdateInstall() : "{}") || {};
    } catch (e) {
      st = { error: e.message };
    }
    emitAppUpdate(st);
    if (!st.error) toast("Скачивание обновления началось");
    else if (st.error) toast(st.error, "err");
    pollAppUpdate(0);
  }

  /* ----------------------------------------------------------- события SSE --- */

  let es = null;
  function connectEvents() {
    if (es) { try { es.close(); } catch (e) {} }
    es = new EventSource(API + "/api/event" + (TOKEN ? "?token=" + encodeURIComponent(TOKEN) : ""));
    es.onopen = () => { BS.online = true; BS.status = "готов"; };
    es.onerror = () => { BS.online = false; BS.status = "нет связи с OpenCode"; push(); };
    es.onmessage = (e) => {
      let ev;
      try { ev = JSON.parse(e.data); } catch (err) { return; }
      onEvent(ev).catch(err => log("event: " + err.message));
    };
  }

  async function onEvent(ev) {
    const t = ev.type || "";
    const d = ev.data || {};
    const sid = d.sessionID;
    BS.events++;
    BS.lastEvent = t;
    BS.online = true;
    BS.status = "готов";

    if (t === "server.connected") return;

    if (t === "session.renamed") {
      for (const c of BS.chats) if (c.id === sid) c.title = d.title;
      if (sid === BS.sid) BS.chat.title = d.title;
      schedulePush(80);
      return;
    }
    if (t === "session.deleted") {
      BS.chats = BS.chats.filter(c => c.id !== sid);
      delete BS.done[sid];
      if (sid === BS.sid) {
        if (BS.chats.length) await openSession(BS.chats[0].id);
        else { BS.sid = null; BS.msgs = new Map(); BS.order = []; BS.chat = {}; applyState(); }
      } else schedulePush(80);
      return;
    }
    if (t === "session.created") {
      await refreshChats();
      handle({ t: "chats", items: BS.chats });
      return;
    }
    if (t === "session.execution.started") {
      if (sid) {
        BS.active.add(sid);
        if (sid === BS.sid) BS.execDone = false;
      }
      schedulePush(60);
      return;
    }
    if (t === "session.execution.succeeded" || t === "session.execution.failed"
        || t === "session.execution.interrupted") {
      if (sid) {
        BS.active.delete(sid);
        BS.done[sid] = Date.now() / 1000;
      }
      if (sid === BS.sid) {
        try { await tailRefresh(); await syncMeta(); } catch (e) {}
      }
      if (t === "session.execution.failed") {
        const err = d.error || d.message || "";
        toast("Ошибка выполнения: " + String(err).slice(0, 200), "err");
      }
      push();
      try { await refreshChats(); handle({ t: "chats", items: BS.chats }); } catch (e) {}
      scheduleAsks(200);
      return;
    }
    if (t.indexOf("form.") === 0 || t.indexOf("permission.") === 0 || t === "session.permissions") {
      if (sid === BS.sid) scheduleAsks(150);
      return;
    }
    if (t === "session.usage.updated") {
      if (sid === BS.sid) {
        BS.usage = { cost: d.cost || 0, tokens: d.tokens || null };
        schedulePush(200);
      }
      return;
    }
    if (t === "session.model.selected") {
      if (sid === BS.sid) { await syncMeta(); schedulePush(80); }
      return;
    }
    if (t === "websearch.updated") {
      if (!BS.wsTimer) {
        BS.wsTimer = setTimeout(() => { BS.wsTimer = null; loadWebsearch(); }, 400);
      }
      return;
    }
    if (t.indexOf("provider.") === 0 || t.indexOf("model.") === 0) return;

    if (sid && sid === BS.sid) {
      if (t === "session.text.delta" || t === "session.reasoning.delta") {
        BS.execDone = false;
        BS.active.add(BS.sid);
        schedulePush(350, true);
      } else {
        schedulePush(300, true);
      }
      scheduleAsks(1200);
    }
  }

  /* --------------------------------------------------------- периодика --- */

  async function busyPoll() {
    try {
      if (BS.sid && BS.online) {
        if (BS.active.has(BS.sid) || busy()) {
          await tailRefresh();
          push();
        }
      }
    } catch (e) { BS.lastErr = "poll: " + e.message; log(BS.lastErr); }
  }

  async function slowPoll() {
    try {
      if (!BS.online) return;
      await refreshActive();
      if (BS.sid && BS.active.has(BS.sid)) {
        await refreshAsks();
        await tailRefresh();
        push();
      }
    } catch (e) {}
  }

  async function refreshAll(full) {
    await refreshChats();
    await refreshActive();
    if (!BS.sid && BS.chats.length) BS.sid = BS.chats[0].id;
    if (BS.sid) {
      try {
        const s = await json("GET", "/api/session/" + BS.sid, null, 30000);
        if (s && s.data) BS.chat = s.data;
      } catch (e) {}
      await refreshAsks();
      await tailRefresh();
    }
    // Периодическая сверка (команда refresh) не должна перерисовывать весь чат:
    // шлём только изменения, иначе экран мигает каждые ~25 секунд.
    if (full === false) push();
    else applyState();
  }

  /* ------------------------------------------------------------- запуск --- */

  async function start() {
    if (BS.started) return;
    BS.started = true;
    injectUiFixes();
    try {
      const h = await android("home");
      if (h && h.path) BS.home = h.path;
    } catch (e) {}
    try {
      const info = await json("GET", "/api/info", null, 15000);
      BS.version = info.version || "";
      BS.online = true;
      BS.status = "готов";
    } catch (e) {
      BS.online = false;
      BS.status = "OpenCode ещё не запустился";
      setTimeout(start, 1500);
      BS.started = false;
      return;
    }

    try {
      await refreshChats();
      await refreshActive();
      let sid = null;
      try { sid = localStorage.getItem("oc_last_session"); } catch (e) {}
      if (!sid || !BS.chats.some(c => c.id === sid)) sid = BS.chats.length ? BS.chats[0].id : null;
      if (sid) await openSession(sid);
      else await createSession(BS.home);
      if (!BS.sid) applyState();
    } catch (e) {
      toast("Ошибка запуска: " + e.message, "err");
      applyState();
    }

    connectEvents();
    if (!BS.timers) {
      BS.timers = true;
      setInterval(busyPoll, 1200);
      setInterval(slowPoll, 3500);
      setInterval(() => { if (!BS.online) { connectEvents(); } }, 8000);
    }
  }

  function injectUiFixes() {
    // Кнопка справа в шапке — открывает те же настройки, что и шестерёнка в меню.
    const pb = document.getElementById("panebtn");
    if (pb) {
      pb.textContent = "⚙";
      pb.title = "Настройки";
      pb.onclick = (e) => {
        e.preventDefault();
        if (typeof window.openSettings === "function") window.openSettings();
        else if (BR.openSettings) BR.openSettings();
      };
    }
    // «Установить APK» с ПК больше не нужно.
    const tapk = document.getElementById("tapk");
    if (tapk) tapk.style.display = "none";
  }

  // страница вызывает send()/connect(); они переопределены в page.html
  window.OCBridge = {
    start, send,
    get sid() { return BS.sid; },
    get home() { return BS.home; },
    state: BS,
    _debug: { BS, tailRefresh, push, applyState, state, refreshAsks, refreshChats, openSession, syncMeta, renderMessage, fetchMessages, connectEvents, refreshActive }
  };
})();
