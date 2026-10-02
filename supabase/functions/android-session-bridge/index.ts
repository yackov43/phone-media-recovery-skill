import { registerAppTool } from "npm:@modelcontextprotocol/ext-apps@2.0.0/server";
import { McpServer, WebStandardStreamableHTTPServerTransport } from "npm:@modelcontextprotocol/server@2.0.0";
import { z } from "npm:zod@4.6.5";

const SUPABASE_URL=Deno.env.get("SUPABASE_URL")||"https://dwwsjglbhzmxspjogjvq.supabase.co";
const SUPABASE_SERVICE_ROLE_KEY=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";

const ANDROID_DEFAULT_AGENT_SUITE = [
  "flow_qa","ui_ux","security","performance",
  "reliability","print_fidelity","regression","mobile_integration"
];

const ANDROID_AGENT_SKILL_VERSION = "2026-10-02.v2";

const ANDROID_AGENT_COMMON_CONTRACT = [
  "IDENTITY: You are one Mini-Agent pass of the SAME GPT session. You are not an external model, not an OpenAI API worker, and not a decorative label. Your role is logically isolated: stay inside your assigned specialty and do not silently take over another agent's responsibilities.",
  "EVIDENCE RULE: Evidence first. Prefer direct evidence from the real Android device, PrintMaster tools, logs, database state, screenshots, UI tree, generated files, and measured timestamps. Never mark PASS because something should work. Separate OBSERVED, INFERRED, and NOT TESTED.",
  "EXECUTION RULE: When safe, reproduce the relevant path yourself. Verify state before an action and verify the resulting state after it. For a failure, capture the exact step, visible state, log/event, processingId/runId/commandId when available, and the smallest reliable reproduction.",
  "PASS/FAIL RULE: PASS requires direct evidence that the required invariant held. ISSUE means a confirmed defect or a required condition not proven. BLOCKED means testing cannot safely continue because of an external state, missing permission, active sensitive screen, unavailable native tool, or other dependency. Do not convert BLOCKED into PASS.",
  "SAFETY RULE: Do not disrupt calls, payments, authentication screens, personal messages, or other sensitive foreground activity. Avoid destructive actions unless explicitly required and authorized. Do not expose secrets, tokens, private image bytes, or service-role credentials in findings.",
  "REPORTING CONTRACT: Return a compact but auditable report containing: scope tested; evidence collected; checks performed; PASS items; ISSUE items with severity and reproduction; BLOCKED/NOT TESTED items; exact next action. Record only claims you can support.",
  "HANDOFF RULE: Do not fix by assumption. If your role finds a defect that belongs to another role, record a cross-role handoff but still finish your own checklist. The coordinator GPT combines the passes only after each role has independently reported."
].join("\n");

const ANDROID_AGENT_ROLE_GUIDES:Record<string,string> = {
  flow_qa: [
    "ROLE: FLOW QA / END-TO-END ORCHESTRATION.",
    "MISSION: Prove the real user journey works from the first actionable screen to the final artifact. Discover where the flow actually stops, loops, skips a mandatory stage, shows stale state, or reports success too early.",
    "REQUIRED METHOD:",
    "1. Establish starting state on the real phone and backend: app version, connection state, Accessibility state, active conversation, PrintMaster buildId, pending run and processingId.",
    "2. Walk the primary happy path one transition at a time. For PrintMaster: source acquisition, preview/source readiness, settings, category/size selection, exact target calculation, session context write, source binding, follow-up dispatch, GPT_PROCESSING, native image edit, result receipt, PNG encoding, PNG verification, READY and download.",
    "3. At each transition verify both sides when possible: visible UI plus backend/log/tool state. A visible spinner without the matching backend stage is not progress.",
    "4. Validate state-machine invariants: no skipped prerequisite; same processingId through the run; correct sourceFileId/resultFileId; no duplicate follow-up or duplicate processing turn.",
    "5. Test safe back/foreground transitions including system picker, Accessibility settings, ChatGPT app switching and result screen return.",
    "6. Exercise at least one controlled timeout/error path and confirm truthful recovery instead of fake success.",
    "MANDATORY PRINTMASTER GATES: SOURCE_READY before selection; FOLLOWUP_SENT proves dispatch only; GPT_PROCESSING must precede native image edit; RESULT_RECEIVED must refer to the native edited result; 100%/READY is forbidden until the verified full-size PNG exists.",
    "FAIL CONDITIONS: stuck progress, duplicate controls, stale widget, wrong conversation/source, hidden timeout, fake 100%, or result shown without a verified file.",
    "OUTPUT EMPHASIS: exact blocking transition and shortest reliable reproduction first."
  ].join("\n"),

  ui_ux: [
    "ROLE: UI / UX QUALITY AGENT.",
    "MISSION: Determine whether a user can understand current state, next action, success/failure and recovery without guessing. Test the real Android UI.",
    "STATE MATRIX: first launch; Accessibility missing; disconnected; code generated; connecting; paired awaiting heartbeat; fully live; disconnecting; disconnected after prior connection; Agent Crew configured/running; error; expired code; network unavailable.",
    "CHECKS:",
    "1. Hierarchy: one clear primary action; no contradictory or duplicate connect/pair sections; status matches backend reality.",
    "2. Progressive disclosure: Connect only after valid code; Disconnect only when paired; permission CTA only when permission is missing.",
    "3. Copy: understandable Hebrew/English, actionable errors, no leaked internal engineering jargon in normal UI.",
    "4. Motion: splash, press, loading and state transitions are smooth and never imply success before confirmation.",
    "5. Accessibility: contrast, touch target size, RTL, text scaling, focus order, semantic labels and no color-only meaning.",
    "6. Mobile layout: no clipped text, hidden CTA, keyboard overlap, impossible scroll target or controls under system bars.",
    "7. Recovery: expired code, network loss, revoked Accessibility and failed send all expose a next step.",
    "PASS STANDARD: a first-time user can connect and understand connected/disconnected states without external instructions.",
    "OUTPUT EMPHASIS: screenshot/state evidence plus desired behavior for every issue."
  ].join("\n"),

  security: [
    "ROLE: SECURITY / PRIVACY AGENT.",
    "MISSION: Verify the bridge gives minimum necessary control to the authorized device/session, can be revoked, does not expose secrets and does not create a public remote shell.",
    "THREAT MODEL: guessed/replayed pairing code; leaked device token; stale paired device; malicious public endpoint caller; privileged database RPC; exposed service-role secret; command injection; unauthorized second device; screenshot/image leakage; persistence after disconnect.",
    "CHECKS:",
    "1. Pairing: server-generated randomness, short expiry, single use, exact requestId acceptance, old pairing cannot acknowledge a new code, used/expired codes rejected.",
    "2. Authentication: random per-device secret local to device; server stores hash; HTTPS header transport; service-role key never in APK/UI.",
    "3. Authorization: commands/results scoped to device; active device explicit; disabled/disconnected device cannot poll/execute.",
    "4. Revocation: Disconnect disables device, clears active control and expires pending/running commands; reconnect requires fresh authorization.",
    "5. Exposure: RLS, grants, SECURITY DEFINER functions, anonymous/authenticated EXECUTE, Edge Function validation and legacy RPC paths.",
    "6. Data minimization: PrintMaster NO_IMAGE_TRANSFER remains true; do not route private source-image bytes through Supabase/MCP. Android QA screenshots are a separate control-plane feature.",
    "7. Accessibility: permission is user-granted and revocable; avoid hidden actions on sensitive screens.",
    "8. Logging: no raw secrets/tokens/private message content/full image payloads in ordinary logs.",
    "FAIL CONDITIONS: reusable pairing code, unauthenticated command path, secret in repo/APK/log, disconnect leaving control active, service-role key client-side, or unnecessary public privileged RPC.",
    "OUTPUT EMPHASIS: severity, exploit preconditions, concrete evidence and minimal remediation."
  ].join("\n"),

  performance: [
    "ROLE: PERFORMANCE / EFFICIENCY AGENT.",
    "MISSION: Keep real-device control responsive without unnecessary battery, CPU, memory, network or database load. Measure where possible.",
    "CHECKS:",
    "1. Polling interval and command pickup latency; detect tight loops, duplicate pollers, recursive triggers or retry storms; only one polling loop per service.",
    "2. Sample ping/tap/screenshot/UI-tree round trips and separate queue wait, device execution and result upload where timestamps exist.",
    "3. Compare metadata payloads with screenshot/base64/UI-tree payloads; flag repeated expensive captures without need.",
    "4. Evaluate background wakeups, retry behavior offline and whether backoff prevents backend hammering.",
    "5. Inspect bitmap/base64 duplication, large JSON/string creation and memory-heavy full-resolution paths.",
    "6. Review database/function hot queries, indexes, stale cleanup and log volume.",
    "7. Ensure splash/animations/state timers are lightweight and do not trigger expensive work every frame.",
    "8. PrintMaster native image work must not be proxied through the Android bridge.",
    "PASS STANDARD: interactive control, no flood/loop, expensive payloads only on demand.",
    "OUTPUT EMPHASIS: measured/configured values, bottleneck, impact and smallest evidence-based improvement."
  ].join("\n"),

  reliability: [
    "ROLE: RELIABILITY / RECOVERY AGENT.",
    "MISSION: Ensure bridge and PrintMaster recover predictably and never leave false-connected, stale-processing or duplicate-command state.",
    "SCENARIOS:",
    "1. Background/reopen Bridge UI; Accessibility service should continue and reopened UI should reflect server reality.",
    "2. Disable/enable Accessibility; verify onboarding/recovery, polling restart and no duplicate workers.",
    "3. Network loss/recovery; transient failures must not erase valid pairing and recovery should resume without re-pair unless revoked.",
    "4. Pairing expiry/retry; new code must not be confused with previous request and exact requestId remains authoritative.",
    "5. Command lifecycle pending → running/claimed → done/error/expired; stale commands expire; async screenshot cannot leave worker permanently busy.",
    "6. Disconnect/reconnect reliably stops and restores control without reinstall.",
    "7. PrintMaster FOLLOWUP_SENT without GPT_PROCESSING must fail visibly after watchdog; retry must not reuse stale/poisoned processingId.",
    "8. Sensitive foreground states such as calls/authentication should BLOCK automation rather than receive taps.",
    "FAIL CONDITIONS: false connected indicator, stuck busy flag, duplicate polling workers, stale command execution, silent timeout or reinstall required for normal recovery.",
    "OUTPUT EMPHASIS: recovery time, required user action and truthfulness of state throughout."
  ].join("\n"),

  print_fidelity: [
    "ROLE: PRINT FIDELITY / OUTPUT INTEGRITY AGENT.",
    "MISSION: Prove PrintMaster returns a real edited image faithful to the user's source and a real downloadable print file at exact requested pixels and DPI.",
    "CHECKS:",
    "1. Source identity: bound source belongs to processingId; no substitute image or wrong attachment.",
    "2. Edit fidelity: preserve identity, face, composition, recognizable objects, legible text/logos, color intent and lighting character. Improve blur/noise/JPEG artifacts and micro-detail without redesign/invention.",
    "3. Fit: contain keeps whole source; cover crops minimally protecting faces/text/logos; extend expands canvas consistently.",
    "4. Geometry: recompute target pixels from physical size/DPI. 10×10 cm at 300 DPI must be 1181×1181. Orientation/target stays exact.",
    "5. Native processing: GPT_PROCESSING then host-native image edit on bound source; MCP/plugin does not pretend to render.",
    "6. Final file: valid PNG, actual raster exact, size > 0, DPI metadata/pHYs matches selected DPI within encoding tolerance, no thumbnail substitute, expected filename.",
    "7. Delivery: preview matches verified file; full-resolution download enabled; before/after coherent.",
    "8. Completion: 75 RESULT_RECEIVED, 85 PNG_ENCODING, 95 PNG_VERIFIED, 100 READY only after file checks.",
    "FAIL CONDITIONS: wrong raster/source, invented content, missing DPI, corrupt PNG, preview-only result, disabled download, stale processingId or premature 100%.",
    "OUTPUT EMPHASIS: exact pixels/DPI/file validity and visual fidelity grounded in source/result evidence."
  ].join("\n"),

  regression: [
    "ROLE: REGRESSION / CANONICAL-INVARIANT AGENT.",
    "MISSION: Protect fixes already achieved and detect silent return to older behavior.",
    "REGRESSION SET:",
    "1. Clean-session widget boot: no Resource not found, Cannot connect or infinite loading.",
    "2. Version freshness: current backend/build resolves; no silent old cached implementation.",
    "3. No bridge flood from bind_print_source, normal tool result or openai:set_globals; polling remains single-flight/throttled.",
    "4. Legacy pending-source guard returns pending error rather than normal recursive result.",
    "5. No duplicate upload/connect/progress controls.",
    "6. No permanent 5%, 25% or 92% stall; 25% is dispatch only; 100% only after verified file.",
    "7. Source/result previews valid; final download active only for full-resolution verified file.",
    "8. Chosen size/DPI remains exact; native image default size never replaces selected target.",
    "9. New clean session uses current resources without assistant manually guessing an older URI/version.",
    "METHOD: compare each historical bug signature with current direct evidence.",
    "OUTPUT EMPHASIS: bug signature → current evidence → PASS or REGRESSED, with exact tested version/build."
  ].join("\n"),

  mobile_integration: [
    "ROLE: ANDROID / CHATGPT MOBILE INTEGRATION AGENT.",
    "MISSION: Verify bridge behavior through the real ChatGPT Android app and Samsung/Android system surfaces.",
    "CHECKS:",
    "1. Bridge launch: animated splash then truthful connection state. First-time Accessibility onboarding opens system settings and return reflects permission.",
    "2. Pairing: in-app code generation; Connect appears only after valid code; Connect opens ChatGPT and delivers pairing instruction to active conversation; exact request acceptance returns connected state.",
    "3. Disconnect/reconnect from app works without reinstall.",
    "4. Accessibility automation: composer detection, text insertion, send-button detection, tap/swipe, UI tree/global actions with keyboard/RTL states.",
    "5. App switching Bridge ↔ ChatGPT ↔ picker/system settings preserves intent and does not target wrong app/screen.",
    "6. Photo picker/upload: distinguish ChatGPT picker failure from PrintMaster widget failure and verify return to correct conversation.",
    "7. Sensitive foreground policy: calls/video calls, authentication/payments or personal screens cause BLOCKED, not disruptive actions.",
    "8. Samsung constraints: sideload restricted settings, Accessibility lifecycle, background process survival, permission revocation and package visibility.",
    "9. Evidence must come from actual SM-S908E screenshots/UI tree, not desktop/browser emulation.",
    "OUTPUT EMPHASIS: exact mobile state before/after each transition with screenshot/UI-tree evidence when useful."
  ].join("\n")
};

function androidAgentSkill(role:string) {
  const roleGuide = ANDROID_AGENT_ROLE_GUIDES[role] || "";
  return ANDROID_AGENT_COMMON_CONTRACT + "\n\n" + roleGuide;
}

function androidAgentSkillMap(roles:any[]) {
  return Object.fromEntries((roles || []).map(role => [String(role), androidAgentSkill(String(role))]));
}

const ANDROID_BRIDGE_HEADER = "x-android-bridge-token";

function androidServiceHeaders(extra:Record<string,string> = {}) {
  return {
    "apikey": SUPABASE_SERVICE_ROLE_KEY,
    "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

async function loadAndroidAgentRegistry(includeDisabled=false) {
  if (!SUPABASE_SERVICE_ROLE_KEY) throw new Error("ANDROID_AGENT_REGISTRY_SERVICE_ROLE_MISSING");
  const enabledFilter = includeDisabled ? "" : "&enabled=eq.true";
  const url = SUPABASE_URL + "/rest/v1/android_bridge_agent_registry?select=agent_id,title,description,sort_order,enabled,skill_version,system_prompt,checklist,updated_at" + enabledFilter + "&order=sort_order.asc,agent_id.asc";
  const response = await fetch(url, { headers: androidServiceHeaders({ "Cache-Control":"no-store" }) });
  if (!response.ok) throw new Error("ANDROID_AGENT_REGISTRY_READ_FAILED:" + response.status + ":" + await response.text());
  const rows = await response.json();
  return Array.isArray(rows) ? rows : [];
}

function fallbackAndroidAgentRegistry() {
  const fallbackMeta:Record<string,{title:string,description:string,sort_order:number}> = {
    flow_qa:{title:"Flow QA",description:"End-to-end flow validation.",sort_order:10},
    ui_ux:{title:"UI / UX",description:"Real-device UI and usability validation.",sort_order:20},
    security:{title:"Security",description:"Pairing, authorization, privacy and attack-surface review.",sort_order:30},
    performance:{title:"Performance",description:"Latency, polling, battery, memory and backend efficiency.",sort_order:40},
    reliability:{title:"Reliability",description:"Timeout, retry, reconnect and recovery validation.",sort_order:50},
    print_fidelity:{title:"Print Fidelity",description:"Source fidelity, fit, exact pixels, DPI and final PNG integrity.",sort_order:60},
    regression:{title:"Regression",description:"Known-bug and canonical-invariant regression testing.",sort_order:70},
    mobile_integration:{title:"Mobile Integration",description:"ChatGPT Android, Accessibility and real-device integration.",sort_order:80}
  };
  return ANDROID_DEFAULT_AGENT_SUITE.map(agent_id => ({
    agent_id,
    title:fallbackMeta[agent_id]?.title || agent_id,
    description:fallbackMeta[agent_id]?.description || "",
    sort_order:fallbackMeta[agent_id]?.sort_order || 100,
    enabled:true,
    skill_version:ANDROID_AGENT_SKILL_VERSION,
    system_prompt:androidAgentSkill(agent_id),
    checklist:[],
    updated_at:null
  }));
}

async function getAndroidAgentRegistry(includeDisabled=false) {
  try {
    const rows = await loadAndroidAgentRegistry(includeDisabled);
    if (rows.length) return rows;
  } catch (error) {
    console.error("ANDROID_AGENT_REGISTRY_FALLBACK", String(error?.message || error));
  }
  return fallbackAndroidAgentRegistry().filter(row => includeDisabled || row.enabled);
}

function normalizeAndroidAgentRoles(requested:any, registry:any[]) {
  const enabled = new Set((registry || []).filter(r=>r?.enabled!==false).map(r=>String(r.agent_id)));
  const requestedList = Array.isArray(requested) ? requested.map(v=>String(v)).filter(v=>/^[a-z0-9_]{2,64}$/.test(v)) : [];
  const filtered = Array.from(new Set(requestedList.filter(v=>enabled.has(v)))).slice(0,32);
  return filtered.length ? filtered : Array.from(enabled).slice(0,32);
}

function androidAgentRegistryVersion(roles:string[], registry:any[]) {
  const byId = new Map((registry || []).map(r=>[String(r.agent_id),r]));
  return "registry:" + (roles || []).map(role => {
    const row=byId.get(String(role));
    return String(role) + "@" + String(row?.skill_version || "unknown");
  }).join(",");
}

function androidAgentSkillSnapshot(roles:string[], registry:any[]) {
  const byId = new Map((registry || []).map(r=>[String(r.agent_id),r]));
  return Object.fromEntries((roles || []).map(role => {
    const row=byId.get(String(role));
    return [String(role), {
      agentId:String(role),
      title:String(row?.title || role),
      description:String(row?.description || ""),
      skillVersion:String(row?.skill_version || ANDROID_AGENT_SKILL_VERSION),
      systemPrompt:String(row?.system_prompt || androidAgentSkill(String(role))),
      checklist:Array.isArray(row?.checklist) ? row.checklist : [],
      updatedAt:row?.updated_at || null
    }];
  }));
}

function androidAgentSkillPrompts(roles:string[], registry:any[]) {
  const snapshot=androidAgentSkillSnapshot(roles,registry);
  return Object.fromEntries(Object.entries(snapshot).map(([role,value]:any)=>[role,value.systemPrompt]));
}

async function sha256Hex(value:string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map(v => v.toString(16).padStart(2, "0")).join("");
}

async function verifyAndroidDevice(deviceId:string, token:string) {
  if (!SUPABASE_SERVICE_ROLE_KEY || !deviceId || !token) return false;
  const response = await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_devices?select=device_id,token_hash,enabled&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
    { headers: androidServiceHeaders({ "Cache-Control": "no-store" }) }
  );
  if (!response.ok) return false;
  const rows = await response.json();
  const row = Array.isArray(rows) ? rows[0] : null;
  return !!row?.enabled && row.token_hash === await sha256Hex(token);
}

async function readAndroidDeviceById(deviceId:string) {
  if (!SUPABASE_SERVICE_ROLE_KEY || !deviceId) return null;
  const response = await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_devices?select=device_id,label,last_seen,app_version,enabled,agent_suite,current_session_id&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  if(!response.ok) throw new Error("ANDROID_BRIDGE_DEVICE_READ_FAILED:"+response.status);
  const rows=await response.json();
  return Array.isArray(rows)?rows[0]||null:null;
}

async function createAndroidPairingRequest(deviceId:string, token:string, label:string, appVersion:string, deviceInfo:any, requestedAgentSuite:any) {
  if (!SUPABASE_SERVICE_ROLE_KEY) throw new Error("ANDROID_BRIDGE_SERVICE_ROLE_MISSING");
  if (!deviceId || !token || token.length < 32) throw new Error("ANDROID_PAIRING_INVALID_DEVICE_SECRET");

  await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?device_id=eq.${encodeURIComponent(deviceId)}&used_at=is.null`,
    { method:"DELETE", headers: androidServiceHeaders({ Prefer:"return=minimal" }) }
  );

  const tokenHash = await sha256Hex(token);
  const registry = await getAndroidAgentRegistry(false);
  const suite = normalizeAndroidAgentRoles(requestedAgentSuite, registry);

  for (let attempt = 0; attempt < 8; attempt++) {
    const random = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
    const code = String(random).padStart(6, "0");
    const codeHash = await sha256Hex(code);
    const create = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests`, {
      method:"POST",
      headers: androidServiceHeaders({ Prefer:"return=representation" }),
      body: JSON.stringify({
        code_hash: codeHash,
        device_id: deviceId,
        token_hash: tokenHash,
        label: label || "Android device",
        app_version: appVersion || null,
        device_info: deviceInfo || {},
        requested_agent_suite: suite,
        expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString()
      })
    });
    if (create.ok) {
      const rows = await create.json();
      return { requestId: rows?.[0]?.id || null, code, expiresAt: rows?.[0]?.expires_at || new Date(Date.now()+10*60*1000).toISOString(), agentSuite:suite };
    }
    if (create.status !== 409) throw new Error("ANDROID_PAIRING_CREATE_FAILED:" + create.status + ":" + await create.text());
  }
  throw new Error("ANDROID_PAIRING_CODE_COLLISION");
}

async function acceptAndroidPairingCode(code:string) {
  const normalized = String(code || "").replace(/\D/g, "");
  if (!/^\d{6}$/.test(normalized)) throw new Error("ANDROID_PAIRING_CODE_INVALID");
  const codeHash = await sha256Hex(normalized);
  const read = await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?select=id,device_id,token_hash,label,app_version,requested_agent_suite,expires_at,used_at&code_hash=eq.${codeHash}&limit=1`,
    { headers: androidServiceHeaders({ "Cache-Control":"no-store" }) }
  );
  if (!read.ok) throw new Error("ANDROID_PAIRING_READ_FAILED:" + read.status);
  const rows = await read.json();
  const req = Array.isArray(rows) ? rows[0] : null;
  if (!req || req.used_at || !req.expires_at || Date.parse(req.expires_at) <= Date.now()) throw new Error("ANDROID_PAIRING_CODE_EXPIRED_OR_UNKNOWN");

  const registry = await getAndroidAgentRegistry(false);
  const suite = normalizeAndroidAgentRoles(req.requested_agent_suite, registry);
  const deviceUpsert = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?on_conflict=device_id`, {
    method:"POST",
    headers: androidServiceHeaders({ Prefer:"resolution=merge-duplicates,return=representation" }),
    body: JSON.stringify({
      device_id:req.device_id,
      token_hash:req.token_hash,
      label:req.label || "Android device",
      enabled:true,
      app_version:req.app_version || null,
      agent_suite:suite,
      updated_at:new Date().toISOString()
    })
  });
  if (!deviceUpsert.ok) throw new Error("ANDROID_PAIRING_DEVICE_UPSERT_FAILED:" + deviceUpsert.status + ":" + await deviceUpsert.text());

  const control = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_control_state?on_conflict=id`, {
    method:"POST",
    headers: androidServiceHeaders({ Prefer:"resolution=merge-duplicates,return=minimal" }),
    body: JSON.stringify({ id:1, active_device_id:req.device_id, updated_at:new Date().toISOString() })
  });
  if (!control.ok) throw new Error("ANDROID_PAIRING_CONTROL_STATE_FAILED:" + control.status);

  await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?id=eq.${req.id}`, {
    method:"PATCH",
    headers: androidServiceHeaders({ Prefer:"return=minimal" }),
    body: JSON.stringify({ used_at:new Date().toISOString() })
  });

  return { deviceId:req.device_id, label:req.label || "Android device", appVersion:req.app_version || null, agentSuite:suite };
}

async function disconnectAndroidDevice(deviceId:string, token:string) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const disabled = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?device_id=eq.${encodeURIComponent(deviceId)}`, {
    method:"PATCH",
    headers: androidServiceHeaders({ Prefer:"return=minimal" }),
    body: JSON.stringify({ enabled:false, updated_at:new Date().toISOString() })
  });
  if (!disabled.ok) throw new Error("ANDROID_DISCONNECT_FAILED:" + disabled.status);

  await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_control_state?id=eq.1&active_device_id=eq.${encodeURIComponent(deviceId)}`, {
    method:"PATCH",
    headers: androidServiceHeaders({ Prefer:"return=minimal" }),
    body: JSON.stringify({ active_device_id:null, updated_at:new Date().toISOString() })
  });
  await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_commands?device_id=eq.${encodeURIComponent(deviceId)}&status=in.(pending,running)`, {
    method:"PATCH",
    headers: androidServiceHeaders({ Prefer:"return=minimal" }),
    body: JSON.stringify({ status:"expired", completed_at:new Date().toISOString() })
  });
  return true;
}

async function setAndroidAgentSuite(deviceId:string, token:string, roles:any) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const registry = await getAndroidAgentRegistry(false);
  const suite = normalizeAndroidAgentRoles(roles, registry);
  const update = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?device_id=eq.${encodeURIComponent(deviceId)}`, {
    method:"PATCH",
    headers: androidServiceHeaders({ Prefer:"return=representation" }),
    body: JSON.stringify({ agent_suite:suite, updated_at:new Date().toISOString() })
  });
  if (!update.ok) throw new Error("ANDROID_AGENT_SUITE_UPDATE_FAILED:" + update.status + ":" + await update.text());
  return suite;
}


async function createAndroidSessionPairingRequest(
  deviceId:string,
  token:string,
  label:string,
  requestedAgentSuite:any,
  chatKey?:string,
  chatTitle?:string,
  isPrimary=false
) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const registry=await getAndroidAgentRegistry(false);
  const suite=normalizeAndroidAgentRoles(requestedAgentSuite,registry);

  for(let attempt=0;attempt<8;attempt++){
    const random=crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
    const code=String(random).padStart(6,"0");
    const codeHash=await sha256Hex(code);
    const create=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_session_pairing_requests`,{
      method:"POST",
      headers:androidServiceHeaders({Prefer:"return=representation"}),
      body:JSON.stringify({
        code_hash:codeHash,
        device_id:deviceId,
        label:(label||chatTitle||"GPT Session").slice(0,120),
        chat_key:chatKey||null,
        chat_title:(chatTitle||label||"GPT Session").slice(0,240),
        is_primary:!!isPrimary,
        requested_agent_suite:suite,
        expires_at:new Date(Date.now()+10*60*1000).toISOString()
      })
    });
    if(create.ok){
      const rows=await create.json();
      return {
        requestId:rows?.[0]?.id||null,
        code,
        expiresAt:rows?.[0]?.expires_at||new Date(Date.now()+10*60*1000).toISOString(),
        label:(label||chatTitle||"GPT Session").slice(0,120),
        chatKey:chatKey||null,
        chatTitle:(chatTitle||label||"GPT Session").slice(0,240),
        isPrimary:!!isPrimary,
        agentSuite:suite
      };
    }
    if(create.status!==409) throw new Error("ANDROID_SESSION_PAIRING_CREATE_FAILED:"+create.status+":"+await create.text());
  }
  throw new Error("ANDROID_SESSION_PAIRING_CODE_COLLISION");
}

async function acceptAndroidSessionPairingCode(code:string, labelOverride?:string) {
  const normalized=String(code||"").replace(/\D/g,"");
  if(!/^\d{6}$/.test(normalized)) throw new Error("ANDROID_SESSION_PAIRING_CODE_INVALID");
  const codeHash=await sha256Hex(normalized);
  const read=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_session_pairing_requests?select=id,device_id,label,chat_key,chat_title,is_primary,requested_agent_suite,expires_at,used_at,session_id&code_hash=eq.${codeHash}&limit=1`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  if(!read.ok) throw new Error("ANDROID_SESSION_PAIRING_READ_FAILED:"+read.status);
  const rows=await read.json();
  const req=Array.isArray(rows)?rows[0]:null;
  if(!req || req.used_at || !req.expires_at || Date.parse(req.expires_at)<=Date.now()){
    throw new Error("ANDROID_SESSION_PAIRING_CODE_EXPIRED_OR_UNKNOWN");
  }

  const registry=await getAndroidAgentRegistry(false);
  let suite=normalizeAndroidAgentRoles(req.requested_agent_suite,registry);
  let session:any=null;

  if(req.chat_key){
    const existingRead=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_sessions?select=*&device_id=eq.${encodeURIComponent(req.device_id)}&chat_key=eq.${encodeURIComponent(req.chat_key)}&limit=1`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    const existingRows=existingRead.ok?await existingRead.json():[];
    const existing=Array.isArray(existingRows)?existingRows[0]||null:null;
    if(existing){
      if(Array.isArray(existing.agent_suite) && existing.agent_suite.length){
        suite=normalizeAndroidAgentRoles(existing.agent_suite,registry);
      }
      const reconnect=await fetch(
        `${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(existing.session_id)}`,
        {
          method:"PATCH",
          headers:androidServiceHeaders({Prefer:"return=representation"}),
          body:JSON.stringify({
            label:String(labelOverride||req.chat_title||req.label||existing.label||"GPT Session").slice(0,120),
            chat_title:String(req.chat_title||existing.chat_title||req.label||"GPT Session").slice(0,240),
            status:"connected",
            enabled:true,
            agent_suite:suite,
            last_seen:new Date().toISOString(),
            updated_at:new Date().toISOString()
          })
        }
      );
      if(!reconnect.ok) throw new Error("ANDROID_SESSION_RECONNECT_FAILED:"+reconnect.status+":"+await reconnect.text());
      const reconnectRows=await reconnect.json();
      session=Array.isArray(reconnectRows)?reconnectRows[0]||null:null;
    }
  }

  if(!session){
    const sessionCreate=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_sessions`,{
      method:"POST",
      headers:androidServiceHeaders({Prefer:"return=representation"}),
      body:JSON.stringify({
        device_id:req.device_id,
        label:String(labelOverride||req.chat_title||req.label||"GPT Session").slice(0,120),
        chat_key:req.chat_key||null,
        chat_title:req.chat_title||req.label||null,
        status:"connected",
        enabled:true,
        agent_suite:suite,
        last_seen:new Date().toISOString(),
        updated_at:new Date().toISOString()
      })
    });
    if(!sessionCreate.ok) throw new Error("ANDROID_SESSION_CREATE_FAILED:"+sessionCreate.status+":"+await sessionCreate.text());
    const createdRows=await sessionCreate.json();
    session=createdRows?.[0];
  }
  if(!session?.session_id) throw new Error("ANDROID_SESSION_ID_MISSING");

  await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_session_pairing_requests?id=eq.${req.id}`,{
    method:"PATCH",
    headers:androidServiceHeaders({Prefer:"return=minimal"}),
    body:JSON.stringify({used_at:new Date().toISOString(),session_id:session.session_id})
  });

  if(req.is_primary){
    await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?device_id=eq.${encodeURIComponent(req.device_id)}`,{
      method:"PATCH",
      headers:androidServiceHeaders({Prefer:"return=minimal"}),
      body:JSON.stringify({current_session_id:session.session_id,updated_at:new Date().toISOString()})
    });
  }

  const registryById=new Map(registry.map((row:any)=>[String(row.agent_id),row]));
  const agentLabels=suite.map((role:string)=>String(registryById.get(role)?.title||role));
  return {
    sessionId:session.session_id,
    deviceId:req.device_id,
    label:session.label,
    chatTitle:session.chat_title||session.label,
    chatKey:session.chat_key||req.chat_key||null,
    isPrimary:!!req.is_primary,
    agentSuite:suite,
    agentLabels,
    status:"connected"
  };
}

async function syncDiscoveredChats(deviceId:string, token:string, chats:any[]) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const clean=(Array.isArray(chats)?chats:[])
    .map((x:any)=>({
      chat_key:String(x?.chatKey||"").slice(0,160),
      title:String(x?.title||"").trim().slice(0,240),
      source:"android_accessibility",
      last_seen:new Date().toISOString(),
      metadata:{
        ordinal:Number.isFinite(Number(x?.ordinal))?Number(x.ordinal):null,
        visibleAtSync:x?.visibleAtSync!==false
      }
    }))
    .filter((x:any)=>/^[a-f0-9]{16,160}$/i.test(x.chat_key) && x.title.length>0)
    .slice(0,500);

  if(!clean.length) return [];
  const upsert=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_discovered_chats?on_conflict=device_id,chat_key`,{
    method:"POST",
    headers:androidServiceHeaders({Prefer:"resolution=merge-duplicates,return=representation"}),
    body:JSON.stringify(clean.map((row:any)=>({...row,device_id:deviceId,updated_at:new Date().toISOString()})))
  });
  if(!upsert.ok) throw new Error("ANDROID_DISCOVERY_SYNC_FAILED:"+upsert.status+":"+await upsert.text());
  const rows=await upsert.json();
  return Array.isArray(rows)?rows:[];
}

async function listDiscoveredChats(deviceId:string, token:string) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const read=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_discovered_chats?select=chat_key,title,last_seen,metadata,updated_at&device_id=eq.${encodeURIComponent(deviceId)}&order=last_seen.desc,title.asc`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  if(!read.ok) throw new Error("ANDROID_DISCOVERY_LIST_FAILED:"+read.status);
  const rows=await read.json();
  return Array.isArray(rows)?rows:[];
}

async function registerDiscoveredChatSession(
  deviceId:string,
  token:string,
  chatKey:string,
  title:string,
  roles:any
) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const registry=await getAndroidAgentRegistry(false);
  const suite=normalizeAndroidAgentRoles(roles,registry);

  const existingRead=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_sessions?select=*&device_id=eq.${encodeURIComponent(deviceId)}&chat_key=eq.${encodeURIComponent(chatKey)}&limit=1`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  const existingRows=existingRead.ok?await existingRead.json():[];
  const existing=Array.isArray(existingRows)?existingRows[0]||null:null;
  if(existing){
    const update=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(existing.session_id)}`,
      {
        method:"PATCH",
        headers:androidServiceHeaders({Prefer:"return=representation"}),
        body:JSON.stringify({
          label:title||existing.label||"GPT Session",
          chat_title:title||existing.chat_title||null,
          status:"connected",
          enabled:true,
          agent_suite:suite,
          updated_at:new Date().toISOString()
        })
      }
    );
    if(!update.ok) throw new Error("ANDROID_DISCOVERED_SESSION_UPDATE_FAILED:"+update.status);
    const rows=await update.json();
    return Array.isArray(rows)?rows[0]||null:null;
  }

  const create=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_sessions`,{
    method:"POST",
    headers:androidServiceHeaders({Prefer:"return=representation"}),
    body:JSON.stringify({
      device_id:deviceId,
      label:title||"GPT Session",
      chat_key:chatKey,
      chat_title:title||null,
      status:"connected",
      enabled:true,
      agent_suite:suite,
      last_seen:new Date().toISOString(),
      updated_at:new Date().toISOString()
    })
  });
  if(!create.ok) throw new Error("ANDROID_DISCOVERED_SESSION_CREATE_FAILED:"+create.status+":"+await create.text());
  const rows=await create.json();
  return Array.isArray(rows)?rows[0]||null:null;
}

async function listAndroidSessions(deviceId:string, token:string) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const read=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_sessions?select=session_id,label,status,enabled,agent_suite,chat_key,chat_title,last_seen,last_command_at,created_at,updated_at&device_id=eq.${encodeURIComponent(deviceId)}&order=created_at.asc`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  if(!read.ok) throw new Error("ANDROID_SESSION_LIST_FAILED:"+read.status);
  const rows=await read.json();
  return Array.isArray(rows)?rows:[];
}

async function updateAndroidSession(
  deviceId:string,
  token:string,
  sessionId:string,
  changes:{label?:string;roles?:any;status?:string}
) {
  if (!(await verifyAndroidDevice(deviceId, token))) throw new Error("ANDROID_BRIDGE_UNAUTHORIZED");
  const patch:any={updated_at:new Date().toISOString()};
  if(typeof changes.label==="string" && changes.label.trim()) patch.label=changes.label.trim().slice(0,120);
  if(changes.roles!==undefined){
    const registry=await getAndroidAgentRegistry(false);
    patch.agent_suite=normalizeAndroidAgentRoles(changes.roles,registry);
  }
  if(changes.status && ["connected","paused","disconnected"].includes(changes.status)){
    patch.status=changes.status;
    patch.enabled=changes.status!=="disconnected";
  }
  const save=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(sessionId)}&device_id=eq.${encodeURIComponent(deviceId)}`,
    {method:"PATCH",headers:androidServiceHeaders({Prefer:"return=representation"}),body:JSON.stringify(patch)}
  );
  if(!save.ok) throw new Error("ANDROID_SESSION_UPDATE_FAILED:"+save.status+":"+await save.text());
  const rows=await save.json();
  return Array.isArray(rows)?rows[0]||null:null;
}

async function readAndroidSession(sessionId:string) {
  if(!sessionId) return null;
  const read=await fetch(
    `${SUPABASE_URL}/rest/v1/android_bridge_sessions?select=session_id,device_id,label,status,enabled,agent_suite,last_seen,last_command_at&session_id=eq.${encodeURIComponent(sessionId)}&limit=1`,
    {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
  );
  if(!read.ok) throw new Error("ANDROID_SESSION_READ_FAILED:"+read.status);
  const rows=await read.json();
  return Array.isArray(rows)?rows[0]||null:null;
}

async function acquireAndroidSessionLease(sessionId:string, leaseMs=45000) {
  const session=await readAndroidSession(sessionId);
  if(!session?.session_id || !session.enabled || session.status!=="connected"){
    throw new Error("ANDROID_SESSION_NOT_ACTIVE");
  }
  const acquire=await fetch(`${SUPABASE_URL}/rest/v1/rpc/android_bridge_acquire_session_lease`,{
    method:"POST",
    headers:androidServiceHeaders({Prefer:"return=representation"}),
    body:JSON.stringify({
      p_device_id:session.device_id,
      p_session_id:sessionId,
      p_lease_ms:Math.max(5000,Math.min(180000,leaseMs||45000))
    })
  });
  if(!acquire.ok) throw new Error("ANDROID_SESSION_LEASE_RPC_FAILED:"+acquire.status+":"+await acquire.text());
  const rows=await acquire.json();
  const row=Array.isArray(rows)?rows[0]||null:null;
  if(!row?.acquired){
    throw new Error("ANDROID_DEVICE_BUSY_SESSION:"+(row?.holder_session_id||"unknown"));
  }
  return {session,leaseExpiresAt:row.lease_expires_at};
}

async function releaseAndroidSessionLease(sessionId:string) {
  const session=await readAndroidSession(sessionId);
  if(!session?.device_id) return false;
  const release=await fetch(`${SUPABASE_URL}/rest/v1/rpc/android_bridge_release_session_lease`,{
    method:"POST",
    headers:androidServiceHeaders({Prefer:"return=representation"}),
    body:JSON.stringify({p_device_id:session.device_id,p_session_id:sessionId})
  });
  if(!release.ok) return false;
  const value=await release.json();
  return value===true || (Array.isArray(value)&&value[0]===true);
}

async function queueAndroidCommand(action:string, payload:any, timeoutMs:number, sessionId:string) {
  if(!sessionId) throw new Error("ANDROID_SESSION_ID_REQUIRED");
  const session=await readAndroidSession(sessionId);
  if(!session?.session_id || !session.enabled || session.status!=="connected") throw new Error("ANDROID_SESSION_NOT_ACTIVE");

  const device=await readAndroidDeviceById(String(session.device_id));
  if(!device?.enabled) throw new Error("ANDROID_BRIDGE_DEVICE_DISABLED");
  const lastSeenMs=device.last_seen?Date.parse(device.last_seen):0;
  if(!lastSeenMs || Date.now()-lastSeenMs>30000) throw new Error("ANDROID_BRIDGE_DEVICE_OFFLINE");

  await acquireAndroidSessionLease(sessionId,Math.max(15000,Math.min(60000,timeoutMs||25000)));

  const create=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_commands`,{
    method:"POST",
    headers:androidServiceHeaders({Prefer:"return=representation"}),
    body:JSON.stringify({device_id:session.device_id,session_id:sessionId,action,payload:payload||{}})
  });
  if(!create.ok) throw new Error("ANDROID_BRIDGE_COMMAND_CREATE_FAILED:"+create.status+":"+await create.text());

  await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(sessionId)}`,{
    method:"PATCH",
    headers:androidServiceHeaders({Prefer:"return=minimal"}),
    body:JSON.stringify({last_command_at:new Date().toISOString(),updated_at:new Date().toISOString()})
  });

  const created=await create.json();
  const commandId=created?.[0]?.id;
  if(!commandId) throw new Error("ANDROID_BRIDGE_COMMAND_ID_MISSING");

  const deadline=Date.now()+Math.max(3000,Math.min(45000,timeoutMs||25000));
  while(Date.now()<deadline){
    await new Promise(resolve=>setTimeout(resolve,350));
    const read=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_commands?select=id,status,result,image_base64,image_mime,completed_at&id=eq.${commandId}&limit=1`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    if(!read.ok) throw new Error("ANDROID_BRIDGE_COMMAND_READ_FAILED:"+read.status);
    const rows=await read.json();
    const row=Array.isArray(rows)?rows[0]:null;
    if(row?.status==="done"||row?.status==="error") return row;
  }
  throw new Error("ANDROID_BRIDGE_COMMAND_TIMEOUT:"+commandId);
}



function createServer(){
  const server=new McpServer(
    {name:"android-session-bridge",title:"Android Session Bridge",version:"1.0.0"},
    {instructions:"Android Session Bridge is a standalone, session-isolated bridge between the current ChatGPT conversation and a paired Android device. Never guess a device or reuse another conversation's session. Pairing returns an exact sessionId; for every control or Mini-Agent action, always pass that exact sessionId. Physical UI access is serialized by an atomic device lease. Each logical GPT session has an independent Agent suite. Do not reveal device secrets, pairing hashes, or service credentials."}
  );
  registerAppTool(server, "android_pair_device", {
    title: "Android — חבר קוד מהאפליקציה",
    description: "Accept a 6-digit pairing code generated inside Android Session Bridge. This activates that exact device for the private control bridge. Use only a code the user can see in their own app.",
    inputSchema: { code: z.string().regex(/^\\d{6}$/) },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
  }, async ({ code }) => {
    try {
      const paired = await acceptAndroidPairingCode(code);
      return {
        structuredContent: { mode:"ANDROID_PAIRED", ...paired },
        content: [{ type:"text", text:`Android Session Bridge paired: ${paired.label}. Agent crew: ${paired.agentSuite.join(", ")}.` }]
      };
    } catch (error) {
      return { isError:true, content:[{type:"text",text:String(error?.message || error)}] };
    }
  });

  registerAppTool(server, "android_pair_session", {
    title: "Android — חבר סשן GPT",
    description: "Pair THIS ChatGPT conversation as a logical Android Bridge session using a 6-digit session code generated in the Android app. The resulting sessionId is this conversation's channel for device control and Mini-Agent runs. Multiple ChatGPT conversations may be paired to the same phone; physical UI actions are serialized by a device lease to prevent collisions.",
    inputSchema: {
      code: z.string().regex(/^\\d{6}$/),
      label: z.string().min(1).max(120).optional(),
    },
    annotations: { readOnlyHint:false, destructiveHint:false, idempotentHint:false, openWorldHint:false },
  }, async ({ code, label }) => {
    try {
      const paired=await acceptAndroidSessionPairingCode(code,label);
      return {
        structuredContent:{mode:"ANDROID_SESSION_PAIRED",...paired},
        content:[{type:"text",text:`✅ Android Session Bridge חובר בהצלחה לסשן "${paired.chatTitle||paired.label}". מזהה הסשן: ${paired.sessionId}. Mini‑Agents שנבחרו לסשן הזה: ${(paired.agentLabels||paired.agentSuite||[]).join(", ")||"ללא"}. החיבור מבודד: יש להעביר תמיד את sessionId הזה ל-android_control ול-android_agent_suite, ואין להשתמש בערוץ של סשן אחר.`}]
      };
    } catch(error) {
      return {isError:true,content:[{type:"text",text:String(error?.message||error)}]};
    }
  });

  registerAppTool(server, "android_session_manager", {
    title: "Android — ניהול סשנים",
    description: "Manage logical GPT sessions paired to one Android device. action=list shows sessions. acquire/release controls the physical-screen lease. pause/resume/disconnect/rename update one session. Only one session may hold the physical UI lease at a time; backend-only analysis can still run concurrently.",
    inputSchema:{
      action:z.enum(["list","acquire","release","pause","resume","disconnect","rename"]),
      sessionId:z.string().uuid().optional(),
      label:z.string().min(1).max(120).optional(),
      leaseMs:z.number().int().min(5000).max(180000).optional(),
      deviceId:z.string().uuid().optional(),
    },
    annotations:{readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:false},
  }, async ({action,sessionId,label,leaseMs,deviceId})=>{
    if(action==="list"){
      if(!deviceId) return {isError:true,content:[{type:"text",text:"ANDROID_DEVICE_ID_REQUIRED"}]};
      const device=await readAndroidDeviceById(deviceId);
      if(!device?.enabled) return {isError:true,content:[{type:"text",text:"ANDROID_DEVICE_NOT_AVAILABLE"}]};
      const read=await fetch(
        `${SUPABASE_URL}/rest/v1/android_bridge_sessions?select=session_id,label,status,enabled,agent_suite,chat_key,chat_title,last_seen,last_command_at,created_at,updated_at&device_id=eq.${device.device_id}&order=created_at.asc`,
        {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
      );
      const rows=read.ok?await read.json():[];
      return {structuredContent:{mode:"ANDROID_SESSION_LIST",sessions:Array.isArray(rows)?rows:[]},content:[{type:"text",text:`Android Bridge sessions: ${Array.isArray(rows)?rows.length:0}.`}]};
    }
    if(!sessionId) return {isError:true,content:[{type:"text",text:"ANDROID_SESSION_ID_REQUIRED"}]};
    const session=await readAndroidSession(sessionId);
    if(!session) return {isError:true,content:[{type:"text",text:"ANDROID_SESSION_NOT_FOUND"}]};
    const device=await readAndroidDeviceById(String(session.device_id));
    if(!device?.enabled) return {isError:true,content:[{type:"text",text:"ANDROID_DEVICE_NOT_AVAILABLE"}]};
    if(action==="acquire"){
      try{
        const lease=await acquireAndroidSessionLease(sessionId,leaseMs||90000);
        return {structuredContent:{mode:"ANDROID_SESSION_LEASE_ACQUIRED",sessionId,leaseExpiresAt:lease.leaseExpiresAt},content:[{type:"text",text:`Physical UI lease acquired for ${session.label} until ${lease.leaseExpiresAt}.`}]};
      }catch(error){
        return {isError:true,content:[{type:"text",text:String(error?.message||error)}]};
      }
    }
    if(action==="release"){
      await releaseAndroidSessionLease(sessionId);
      return {structuredContent:{mode:"ANDROID_SESSION_LEASE_RELEASED",sessionId},content:[{type:"text",text:`Physical UI lease released for ${session.label}.`}]};
    }
    const status=action==="pause"?"paused":action==="resume"?"connected":action==="disconnect"?"disconnected":undefined;
    const updated=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(sessionId)}&device_id=eq.${encodeURIComponent(device.device_id)}`,
      {method:"PATCH",headers:androidServiceHeaders({Prefer:"return=representation"}),body:JSON.stringify({
        ...(status?{status,enabled:status!=="disconnected"}:{}),
        ...(action==="rename"&&label?{label}:{}),
        updated_at:new Date().toISOString()
      })}
    );
    const rows=updated.ok?await updated.json():[];
    if(!updated.ok||!Array.isArray(rows)||!rows.length) return {isError:true,content:[{type:"text",text:"ANDROID_SESSION_UPDATE_FAILED"}]};
    if(action==="disconnect" || action==="pause") await releaseAndroidSessionLease(sessionId);
    return {structuredContent:{mode:"ANDROID_SESSION_UPDATED",session:rows[0]},content:[{type:"text",text:`Session updated: ${rows[0].label} (${rows[0].status}).`}]};
  });

  registerAppTool(server, "android_agent_suite", {
    title: "Android — צוות סוכני בדיקה",
    description: "Manage the Android Mini-Agent QA crew. These are independent role passes of the SAME GPT in the active ChatGPT session—not external OpenAI API agents and not separate API keys. Every role has a versioned, detailed Skill Contract with mission, scope, mandatory checks, evidence rules, PASS/ISSUE/BLOCKED gates, safety rules and reporting requirements. action=start creates or picks up a tracked run and returns the full Skill Contract for every enabled Mini-Agent. The assistant must execute each role as a distinct pass, follow its contract, use real Android/app/backend evidence, record findings per role with update_role, then finish the run.",
    inputSchema: {
      action: z.enum(["status","start","update_role","finish","cancel"]),
      runId: z.string().uuid().optional(),
      roles: z.array(z.string().regex(/^[a-z0-9_]{2,64}$/)).max(32).optional(),
      role: z.string().regex(/^[a-z0-9_]{2,64}$/).optional(),
      summary: z.string().max(4000).optional(),
      ok: z.boolean().optional(),
      sessionId: z.string().uuid(),
    },
    annotations: { readOnlyHint:false, destructiveHint:false, idempotentHint:false, openWorldHint:false },
  }, async ({ action, runId, roles, role, summary, ok, sessionId }) => {
    const boundSession=await readAndroidSession(sessionId);
    if(!boundSession || !boundSession.enabled || boundSession.status==="disconnected"){
      return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_SESSION_NOT_ACTIVE"}]};
    }
    const device=await readAndroidDeviceById(String(boundSession.device_id));
    if(!device?.enabled) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_DEVICE_NOT_AVAILABLE"}]};
    const registry = await getAndroidAgentRegistry(false);
    const selected = normalizeAndroidAgentRoles(
      Array.isArray(roles) && roles.length ? roles : (boundSession?.agent_suite || device.agent_suite),
      registry
    );
    const currentRegistryVersion = androidAgentRegistryVersion(selected, registry);
    const currentSkillSnapshot = androidAgentSkillSnapshot(selected, registry);

    if (action === "status") {
      const runs = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?select=id,status,roles,findings,skill_version,role_contracts,session_id,created_at,started_at,completed_at&device_id=eq.${device.device_id}${sessionId?`&session_id=eq.${sessionId}`:""}&order=created_at.desc&limit=1`, {headers:androidServiceHeaders({"Cache-Control":"no-store"})});
      const rows = runs.ok ? await runs.json() : [];
      return {
        structuredContent:{mode:"ANDROID_AGENT_STATUS",deviceId:device.device_id,sessionId:sessionId||null,roles:selected,skillVersion:currentRegistryVersion,roleSkills:currentSkillSnapshot,lastRun:Array.isArray(rows)?rows[0]||null:null},
        content:[{type:"text",text:`Agent crew ready: ${selected.join(", ")}`}]
      };
    }

    if (action === "start") {
      const pendingRead=await fetch(
        `${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?select=id,roles,skill_version,role_contracts,session_id&device_id=eq.${device.device_id}&status=eq.pending${sessionId?`&session_id=eq.${sessionId}`:"&session_id=is.null"}&order=created_at.asc&limit=1`,
        {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
      );
      const pendingRows=pendingRead.ok?await pendingRead.json():[];
      const pending=Array.isArray(pendingRows)?pendingRows[0]||null:null;
      if(pending?.id){
        const pendingRoles=Array.isArray(pending.roles)&&pending.roles.length?pending.roles:selected;
        const activate=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?id=eq.${pending.id}`,{
          method:"PATCH",headers:androidServiceHeaders({Prefer:"return=minimal"}),
          body:JSON.stringify({
            status:"running",
            started_at:new Date().toISOString(),
            skill_version:androidAgentRegistryVersion(pendingRoles, registry),
            role_contracts:androidAgentSkillSnapshot(pendingRoles, registry)
          })
        });
        if(!activate.ok) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_PENDING_ACTIVATE_FAILED:"+activate.status}]};
        return {
          structuredContent:{mode:"ANDROID_AGENT_RUN_STARTED",runId:pending.id,roles:pendingRoles,requestedBy:"app",skillVersion:androidAgentRegistryVersion(pendingRoles, registry),roleSkills:androidAgentSkillSnapshot(pendingRoles, registry)},
          content:[{type:"text",text:`Picked up the app-requested Agent Crew run using ${androidAgentRegistryVersion(pendingRoles, registry)}. Execute each role as a separate evidence-first pass, follow its full roleSkills contract, record each role, then finish. Roles: ${pendingRoles.join(", ")}.`}]
        };
      }

      const create = await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs`,{
        method:"POST",
        headers:androidServiceHeaders({Prefer:"return=representation"}),
        body:JSON.stringify({
          device_id:device.device_id,
          session_id:sessionId||null,
          roles:selected,
          status:"running",
          requested_by:"chatgpt_session",
          started_at:new Date().toISOString(),
          skill_version:currentRegistryVersion,
          role_contracts:currentSkillSnapshot
        })
      });
      if(!create.ok) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_RUN_CREATE_FAILED:"+create.status}]};
      const rows=await create.json(); const run=rows?.[0];
      return {
        structuredContent:{mode:"ANDROID_AGENT_RUN_STARTED",runId:run?.id,roles:selected,requestedBy:"chatgpt_session",skillVersion:currentRegistryVersion,roleSkills:currentSkillSnapshot},
        content:[{type:"text",text:`Run every enabled Mini-Agent separately under ${currentRegistryVersion}. Follow each full roleSkills contract, collect direct evidence, record every role with update_role, then finish. Roles: ${selected.join(", ")}.`}]
      };
    }

    if (!runId) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_RUN_ID_REQUIRED"}]};

    if (action === "update_role") {
      if (!role) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_ROLE_REQUIRED"}]};
      const read=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?select=findings,role_contracts,session_id&id=eq.${runId}&device_id=eq.${device.device_id}&limit=1`,{headers:androidServiceHeaders({"Cache-Control":"no-store"})});
      const rows=read.ok?await read.json():[];
      const runRow=Array.isArray(rows)?rows[0]||null:null;
      if(!runRow?.role_contracts || !Object.prototype.hasOwnProperty.call(runRow.role_contracts, role)) {
        return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_ROLE_NOT_IN_RUN:"+role}]};
      }
      const findings=runRow.findings||{};
      findings[role]={ok:ok===true,summary:summary||"",updatedAt:new Date().toISOString()};
      const save=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?id=eq.${runId}&device_id=eq.${device.device_id}`,{
        method:"PATCH",headers:androidServiceHeaders({Prefer:"return=minimal"}),body:JSON.stringify({findings})
      });
      if(!save.ok) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_ROLE_SAVE_FAILED:"+save.status}]};
      return {structuredContent:{mode:"ANDROID_AGENT_ROLE_RECORDED",runId,role,ok:ok===true},content:[{type:"text",text:`Recorded ${role}: ${ok===true?"PASS":"ISSUE"}`}]};
    }

    const targetStatus=action==="cancel"?"cancelled":(ok===false?"failed":"completed");
    const done=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?id=eq.${runId}&device_id=eq.${device.device_id}`,{
      method:"PATCH",headers:androidServiceHeaders({Prefer:"return=minimal"}),body:JSON.stringify({status:targetStatus,completed_at:new Date().toISOString()})
    });
    if(!done.ok) return {isError:true,content:[{type:"text",text:"ANDROID_AGENT_RUN_FINISH_FAILED:"+done.status}]};
    return {structuredContent:{mode:"ANDROID_AGENT_RUN_FINISHED",runId,status:targetStatus},content:[{type:"text",text:`Agent run ${targetStatus}.`}]};
  });

  registerAppTool(server, "android_control", {
    title: "Android — שליטה חיה במכשיר",
    description: "Control the user's paired Android device through the standalone Android Session Bridge backend. Supports live screenshot, accessibility UI tree, tap, swipe, Back/Home/Recents, app launch, text input, text-target tap, and QA-image creation. This is real-device control, not a simulator.",
    inputSchema: {
      action: z.enum(["status","screenshot","ui_tree","tap","swipe","back","home","recents","open_app","input_text","tap_text","save_qa_image"]),
      x: z.number().int().nonnegative().optional(),
      y: z.number().int().nonnegative().optional(),
      x2: z.number().int().nonnegative().optional(),
      y2: z.number().int().nonnegative().optional(),
      durationMs: z.number().int().min(50).max(5000).optional(),
      text: z.string().max(4000).optional(),
      packageName: z.string().max(200).optional(),
      timeoutMs: z.number().int().min(3000).max(45000).optional(),
      sessionId: z.string().uuid(),
    },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
  }, async ({ action, x, y, x2, y2, durationMs, text, packageName, timeoutMs, sessionId }) => {
    const boundSession=await readAndroidSession(sessionId);
    if(!boundSession) return {isError:true,content:[{type:"text",text:"ANDROID_SESSION_NOT_FOUND"}]};
    const device=await readAndroidDeviceById(String(boundSession.device_id));
    if (action === "status") {
      const ageMs = device?.last_seen ? Date.now() - Date.parse(device.last_seen) : null;
      const online = !!device?.enabled && ageMs !== null && ageMs < 30000;
      return {
        structuredContent: { mode: "ANDROID_STATUS", deviceId: device?.device_id || ANDROID_FALLBACK_DEVICE_ID, label: device?.label || "Android", online, lastSeen: device?.last_seen || null, appVersion: device?.app_version || null, agentSuite: Array.isArray(device?.agent_suite) ? device.agent_suite : ANDROID_DEFAULT_AGENT_SUITE },
        content: [{ type: "text", text: online ? `Android device online: ${device?.label || "Android"}.` : `Android device offline or not yet paired. Last seen: ${device?.last_seen || "never"}.` }]
      };
    }
    let bridgeAction=action;
    const payload:any = {};
    if (action === "ui_tree") bridgeAction="snapshot";
    else if (action === "back" || action === "home" || action === "recents") {
      bridgeAction="global"; payload.action=action;
    } else if (action === "open_app") {
      bridgeAction="launch"; payload.package=packageName || "com.openai.chatgpt";
    } else if (action === "input_text") {
      bridgeAction="set_text"; payload.text=text || "";
    } else if (action === "tap_text") {
      bridgeAction="click_text"; payload.text=text || ""; payload.exact=false;
    } else if (action === "save_qa_image") bridgeAction="make_qa_image";

    if (action === "tap") {
      if (x !== undefined) payload.x = x;
      if (y !== undefined) payload.y = y;
    }
    if (action === "swipe") {
      if (x !== undefined) payload.x1 = x;
      if (y !== undefined) payload.y1 = y;
      if (x2 !== undefined) payload.x2 = x2;
      if (y2 !== undefined) payload.y2 = y2;
      if (durationMs !== undefined) payload.duration_ms = durationMs;
    }

    const row = await queueAndroidCommand(bridgeAction, payload, timeoutMs || (action === "screenshot" ? 30000 : 20000), sessionId);
    const resultText = JSON.stringify(row.result || {});
    if (row.status === "error") return { isError: true, content: [{ type: "text", text: `ANDROID_ACTION_FAILED action=${action} result=${resultText}` }] };
    const content:any[] = [];
    if (row.image_base64) content.push({ type: "image", data: row.image_base64, mimeType: row.image_mime || "image/jpeg" });
    content.push({ type: "text", text: `ANDROID_ACTION_OK action=${action} result=${resultText}` });
    return { structuredContent: { mode: "ANDROID_ACTION_RESULT", action, sessionId:sessionId||null, result: row.result || {}, completedAt: row.completed_at || null }, content };
  });

  registerAppTool(server, "select_print_size", {
    title: "חשב גודל ופיקסלים",
    description: "Validate output size and calculate exact pixel dimensions. No image is processed by this tool.",
    inputSchema: {
      width: z.number().positive().max(32767),
      height: z.number().positive().max(32767),
      unit: z.enum(["cm","mm","m","in","px"]),
      dpi: z.number().int().min(72).max(1200),
      label: z.string().max(80).optional(),
      processingId: z.string().min(1).max(64).optional(),
    },
    outputSchema: {
      width: z.number().positive(),
      height: z.number().positive(),
      unit: z.enum(["cm","mm","m","in","px"]),
      dpi: z.number().int(),
      pixelWidth: z.number().int().positive(),
      pixelHeight: z.number().int().positive(),
      label: z.string(),
      status: z.literal("SIZE_SELECTED"),
    },
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    _meta: { ui: { visibility: ["app","model"] }, "openai/widgetAccessible": true },
  }, async ({ width, height, unit, dpi, label, processingId }) => {
    if (processingId) console.log(`PRINTMASTER_SELECT processingId=${processingId} label=${JSON.stringify(label || "")} dpi=${dpi}`);
    const toInches = value => unit === "cm" ? value / 2.54 : unit === "mm" ? value / 25.4 : unit === "m" ? value * 100 / 2.54 : unit === "in" ? value : null;
    const pixelWidth = unit === "px" ? Math.round(width) : Math.round(toInches(width) * dpi);
    const pixelHeight = unit === "px" ? Math.round(height) : Math.round(toInches(height) * dpi);
    if (pixelWidth > 32767 || pixelHeight > 32767 || pixelWidth * pixelHeight > 200_000_000) {
      return { isError: true, content: [{ type: "text", text: "הגודל וה־DPI שנבחרו יוצרים קובץ גדול מדי." }] };
    }
    const selection = { width, height, unit, dpi, pixelWidth, pixelHeight, label: label || `${width}×${height} ${unit}`, status: "SIZE_SELECTED" };
    return {
      structuredContent: selection,
      content: [{ type: "text", text: `Selected ${selection.label} at ${dpi} DPI (${pixelWidth} × ${pixelHeight} pixels). Metadata only; no image was processed.` }],
    };
  });

  return server;
}


const corsHeaders={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Methods":"POST, GET, DELETE, OPTIONS","Access-Control-Allow-Headers":"content-type, mcp-session-id, mcp-protocol-version, accept, x-android-bridge-token, x-android-bridge-version","Access-Control-Expose-Headers":"Mcp-Session-Id","Cache-Control":"no-store, max-age=0","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"};

Deno.serve(async(req:Request)=>{
  if(req.method==="OPTIONS") return new Response(null,{status:204,headers:corsHeaders});

  const url=new URL(req.url);
  const androidBridgeMode=url.searchParams.get("android_bridge");

  if(androidBridgeMode==="pair_begin" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const issued=await createAndroidPairingRequest(
        deviceId,
        token,
        String(body.label||"Android device"),
        String(body.appVersion||""),
        body.deviceInfo||{},
        body.agentSuite||[]
      );
      return new Response(JSON.stringify({
        ok:true,
        requestId:issued.requestId,
        code:issued.code,
        expiresAt:issued.expiresAt,
        agentSuite:issued.agentSuite,
        instruction:`ב-ChatGPT כתוב: חבר את Android Session Bridge עם הקוד ${issued.code}`
      }),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="pair_complete" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const requestId=String(body.requestId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    if(!deviceId || !requestId || !token){
      return new Response(JSON.stringify({ok:false,error:"missing_pair_context"}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
    const tokenHash=await sha256Hex(token);
    const read=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?select=id,device_id,token_hash,label,app_version,requested_agent_suite,expires_at,used_at&id=eq.${encodeURIComponent(requestId)}&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    const rows=read.ok?await read.json():[];
    const reqRow=Array.isArray(rows)?rows[0]||null:null;
    if(!reqRow || reqRow.token_hash!==tokenHash || reqRow.used_at || !reqRow.expires_at || Date.parse(reqRow.expires_at)<=Date.now()){
      return new Response(JSON.stringify({ok:false,error:"pair_request_invalid_or_expired"}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }

    const registry=await getAndroidAgentRegistry(false);
    const suite=normalizeAndroidAgentRoles(reqRow.requested_agent_suite,registry);
    const upsert=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?on_conflict=device_id`,{
      method:"POST",
      headers:androidServiceHeaders({Prefer:"resolution=merge-duplicates,return=representation"}),
      body:JSON.stringify({
        device_id:deviceId,
        token_hash:tokenHash,
        label:reqRow.label||"Android device",
        enabled:true,
        app_version:reqRow.app_version||null,
        agent_suite:suite,
        updated_at:new Date().toISOString()
      })
    });
    if(!upsert.ok){
      return new Response(JSON.stringify({ok:false,error:"device_enable_failed"}),{status:500,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
    await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?id=eq.${encodeURIComponent(requestId)}`,{
      method:"PATCH",
      headers:androidServiceHeaders({Prefer:"return=minimal"}),
      body:JSON.stringify({used_at:new Date().toISOString()})
    });

    return new Response(JSON.stringify({ok:true,paired:true,deviceId,agentSuite:suite}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
  }

  if(androidBridgeMode==="pair_status" && req.method==="GET"){
    const deviceId=url.searchParams.get("device_id")||"";
    const requestId=url.searchParams.get("request_id")||"";
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    const paired=await verifyAndroidDevice(deviceId,token);
    if(requestId){
      const tokenHash=await sha256Hex(token);
      const pending=await fetch(
        `${SUPABASE_URL}/rest/v1/android_bridge_pairing_requests?select=id,device_id,token_hash,expires_at,used_at&id=eq.${encodeURIComponent(requestId)}&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
        {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
      );
      const rows=pending.ok?await pending.json():[];
      const req=Array.isArray(rows)?rows[0]||null:null;
      const valid=!!req && req.token_hash===tokenHash;
      const expired=!req?.expires_at || Date.parse(req.expires_at)<=Date.now();
      const accepted=valid && !!req.used_at && paired;
      return new Response(JSON.stringify({ok:true,paired,requestFound:valid,accepted,expired}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
    }
    return new Response(JSON.stringify({ok:true,paired}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
  }

  if(androidBridgeMode==="disconnect" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      await disconnectAndroidDevice(deviceId,token);
      return new Response(JSON.stringify({ok:true,disconnected:true}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="session_pair_begin" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const issued=await createAndroidSessionPairingRequest(
        deviceId,
        token,
        String(body.label||body.chatTitle||"GPT Session"),
        body.agentSuite||[],
        body.chatKey?String(body.chatKey):undefined,
        body.chatTitle?String(body.chatTitle):undefined,
        body.isPrimary===true
      );
      return new Response(JSON.stringify({
        ok:true,
        requestId:issued.requestId,
        code:issued.code,
        expiresAt:issued.expiresAt,
        label:issued.label,
        chatKey:issued.chatKey,
        chatTitle:issued.chatTitle,
        agentSuite:issued.agentSuite
      }),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="session_pair_status" && req.method==="GET"){
    const deviceId=url.searchParams.get("device_id")||"";
    const requestId=url.searchParams.get("request_id")||"";
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    if(!(await verifyAndroidDevice(deviceId,token))) return new Response(JSON.stringify({ok:false,error:"unauthorized"}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const read=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_session_pairing_requests?select=id,device_id,expires_at,used_at,session_id,label&id=eq.${encodeURIComponent(requestId)}&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    const rows=read.ok?await read.json():[];
    const req=Array.isArray(rows)?rows[0]||null:null;
    const expired=!req?.expires_at || Date.parse(req.expires_at)<=Date.now();
    const accepted=!!req?.used_at && !!req?.session_id;
    return new Response(JSON.stringify({ok:true,requestFound:!!req,accepted,expired,sessionId:req?.session_id||null,label:req?.label||null}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
  }

  if(androidBridgeMode==="discovery_sync" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const rows=await syncDiscoveredChats(deviceId,token,body.chats||[]);
      return new Response(JSON.stringify({ok:true,count:rows.length}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="discovery_list" && req.method==="GET"){
    const deviceId=url.searchParams.get("device_id")||"";
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const chats=await listDiscoveredChats(deviceId,token);
      return new Response(JSON.stringify({ok:true,chats}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="session_register_discovered" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    const chatKey=String(body.chatKey||"");
    const title=String(body.title||"GPT Session");
    try{
      const session=await registerDiscoveredChatSession(deviceId,token,chatKey,title,body.roles||[]);
      return new Response(JSON.stringify({ok:true,session}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="session_list" && req.method==="GET"){
    const deviceId=url.searchParams.get("device_id")||"";
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const sessions=await listAndroidSessions(deviceId,token);
      const deviceRead=await fetch(
        `${SUPABASE_URL}/rest/v1/android_bridge_devices?select=current_session_id&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
        {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
      );
      const deviceRows=deviceRead.ok?await deviceRead.json():[];
      const currentSessionId=Array.isArray(deviceRows)?deviceRows[0]?.current_session_id||null:null;
      return new Response(JSON.stringify({ok:true,sessions,currentSessionId}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="session_update" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    const sessionId=String(body.sessionId||"");
    try{
      const requestedStatus=typeof body.status==="string"?body.status:undefined;
      const session=await updateAndroidSession(deviceId,token,sessionId,{
        label:typeof body.label==="string"?body.label:undefined,
        roles:body.roles,
        status:requestedStatus
      });
      if(requestedStatus==="paused" || requestedStatus==="disconnected"){
        await fetch(
          `${SUPABASE_URL}/rest/v1/android_bridge_commands?session_id=eq.${encodeURIComponent(sessionId)}&status=eq.pending`,
          {
            method:"PATCH",
            headers:androidServiceHeaders({Prefer:"return=minimal"}),
            body:JSON.stringify({status:"expired",completed_at:new Date().toISOString()})
          }
        );
        if(requestedStatus==="disconnected"){
          console.log("ANDROID_SESSION_CANCEL_AGENT_RUNS",sessionId);
          await fetch(
            `${SUPABASE_URL}/rest/v1/android_bridge_agent_runs?session_id=eq.${encodeURIComponent(sessionId)}&status=in.(pending,running)`,
            {
              method:"PATCH",
              headers:androidServiceHeaders({Prefer:"return=minimal"}),
              body:JSON.stringify({status:"cancelled",completed_at:new Date().toISOString()})
            }
          );
        }
        const runningRead=await fetch(
          `${SUPABASE_URL}/rest/v1/android_bridge_commands?select=id&session_id=eq.${encodeURIComponent(sessionId)}&status=eq.running&limit=1`,
          {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
        );
        const runningRows=runningRead.ok?await runningRead.json():[];
        if(!Array.isArray(runningRows) || !runningRows.length){
          await releaseAndroidSessionLease(sessionId);
        }
      }
      return new Response(JSON.stringify({ok:true,session}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="agent_settings" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    try{
      const roles=await setAndroidAgentSuite(deviceId,token,body.roles||[]);
      return new Response(JSON.stringify({ok:true,roles}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }catch(error){
      return new Response(JSON.stringify({ok:false,error:String(error?.message||error)}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    }
  }

  if(androidBridgeMode==="agent_registry" && req.method==="GET"){
    const registry=await getAndroidAgentRegistry(false);
    const publicRows=registry.map(row=>({
      agentId:String(row.agent_id),
      title:String(row.title||row.agent_id),
      description:String(row.description||""),
      sortOrder:Number(row.sort_order||100),
      enabled:row.enabled!==false,
      skillVersion:String(row.skill_version||""),
      checklistCount:Array.isArray(row.checklist)?row.checklist.length:0
    }));
    return new Response(JSON.stringify({ok:true,agents:publicRows}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
  }

  if(androidBridgeMode==="agent_run_request" && req.method==="POST"){
    let body:any={}; try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    const sessionId=body.sessionId?String(body.sessionId):"";
    if(!(await verifyAndroidDevice(deviceId,token))) return new Response(JSON.stringify({ok:false,error:"unauthorized"}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const registry=await getAndroidAgentRegistry(false);

    let savedSuite:any[]=[];
    if(sessionId){
      const session=await readAndroidSession(sessionId);
      if(!session || session.device_id!==deviceId || !session.enabled || session.status==="disconnected"){
        return new Response(JSON.stringify({ok:false,error:"session_not_active"}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
      }
      savedSuite=Array.isArray(session.agent_suite)?session.agent_suite:[];
    }else{
      const deviceRead=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?select=agent_suite&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,{headers:androidServiceHeaders({"Cache-Control":"no-store"})});
      const deviceRows=deviceRead.ok?await deviceRead.json():[];
      savedSuite=Array.isArray(deviceRows)&&Array.isArray(deviceRows[0]?.agent_suite)?deviceRows[0].agent_suite:[];
    }

    const roles=normalizeAndroidAgentRoles(Array.isArray(body.roles)&&body.roles.length?body.roles:savedSuite,registry);
    const skillVersion=androidAgentRegistryVersion(roles,registry);
    const roleContracts=androidAgentSkillSnapshot(roles,registry);
    const create=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_agent_runs`,{
      method:"POST",headers:androidServiceHeaders({Prefer:"return=representation"}),
      body:JSON.stringify({
        device_id:deviceId,
        session_id:sessionId||null,
        roles,
        status:"pending",
        requested_by:"app",
        skill_version:skillVersion,
        role_contracts:roleContracts
      })
    });
    if(!create.ok) return new Response(JSON.stringify({ok:false,error:"run_create_failed"}),{status:500,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const rows=await create.json();
    return new Response(JSON.stringify({ok:true,runId:rows?.[0]?.id||null,sessionId:sessionId||null,roles,status:"pending",skillVersion}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
  }

  if(androidBridgeMode==="poll" && req.method==="GET"){
    const deviceId=url.searchParams.get("device_id")||"";
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    if(!(await verifyAndroidDevice(deviceId,token))) return new Response(JSON.stringify({error:"unauthorized"}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_devices?device_id=eq.${encodeURIComponent(deviceId)}`,{
      method:"PATCH",headers:androidServiceHeaders({Prefer:"return=minimal"}),body:JSON.stringify({last_seen:new Date().toISOString(),app_version:req.headers.get("x-android-bridge-version")||null,updated_at:new Date().toISOString()})
    });
    const leaseRead=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_device_leases?select=session_id,lease_expires_at&device_id=eq.${encodeURIComponent(deviceId)}&limit=1`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    const leaseRows=leaseRead.ok?await leaseRead.json():[];
    const lease=Array.isArray(leaseRows)?leaseRows[0]||null:null;
    const leaseActive=!!lease?.session_id && !!lease?.lease_expires_at && Date.parse(lease.lease_expires_at)>Date.now();

    const pending=await fetch(
      `${SUPABASE_URL}/rest/v1/android_bridge_commands?select=id,action,payload,created_at,session_id,priority&device_id=eq.${encodeURIComponent(deviceId)}&status=eq.pending&expires_at=gt.${encodeURIComponent(new Date().toISOString())}&order=priority.asc,created_at.asc&limit=25`,
      {headers:androidServiceHeaders({"Cache-Control":"no-store"})}
    );
    if(!pending.ok) return new Response(JSON.stringify({error:"poll_failed"}),{status:500,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const rows=await pending.json();
    const command=Array.isArray(rows)
      ? rows.find((row:any)=>{
          const sid=String(row?.session_id||"");
          if(sid) return leaseActive && sid===String(lease.session_id);
          return !leaseActive;
        })
      : null;
    if(!command) return new Response(JSON.stringify({command:null}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
    const claimed=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_commands?id=eq.${command.id}&status=eq.pending`,{
      method:"PATCH",headers:androidServiceHeaders({Prefer:"return=representation"}),body:JSON.stringify({status:"running",claimed_at:new Date().toISOString()})
    });
    const claimedRows=claimed.ok?await claimed.json():[];
    if(!Array.isArray(claimedRows)||!claimedRows.length) return new Response(JSON.stringify({command:null}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
    return new Response(JSON.stringify({command:{id:command.id,action:command.action,payload:command.payload||{},sessionId:command.session_id||null}}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json","Cache-Control":"no-store"}});
  }

  if(androidBridgeMode==="result" && req.method==="POST"){
    let body:any={};
    try{body=await req.json();}catch{}
    const deviceId=String(body.deviceId||"");
    const token=req.headers.get(ANDROID_BRIDGE_HEADER)||"";
    if(!(await verifyAndroidDevice(deviceId,token))) return new Response(JSON.stringify({error:"unauthorized"}),{status:401,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const commandId=String(body.commandId||"");
    if(!commandId) return new Response(JSON.stringify({error:"command_id_missing"}),{status:400,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const update={
      status:body.ok===false?"error":"done",
      result:body.result||{},
      image_base64:typeof body.imageBase64==="string"&&body.imageBase64.length?body.imageBase64:null,
      image_mime:body.imageMime||null,
      completed_at:new Date().toISOString()
    };
    const saved=await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_commands?id=eq.${encodeURIComponent(commandId)}&device_id=eq.${encodeURIComponent(deviceId)}`,{
      method:"PATCH",headers:androidServiceHeaders({Prefer:"return=representation"}),body:JSON.stringify(update)
    });
    if(!saved.ok) return new Response(JSON.stringify({error:"result_write_failed"}),{status:500,headers:{...corsHeaders,"Content-Type":"application/json"}});
    const savedRows=await saved.json();
    const savedCommand=Array.isArray(savedRows)?savedRows[0]||null:null;
    if(savedCommand?.session_id){
      await fetch(`${SUPABASE_URL}/rest/v1/android_bridge_sessions?session_id=eq.${encodeURIComponent(savedCommand.session_id)}`,{
        method:"PATCH",
        headers:androidServiceHeaders({Prefer:"return=minimal"}),
        body:JSON.stringify({last_seen:new Date().toISOString(),updated_at:new Date().toISOString()})
      });
      const sessionAfter=await readAndroidSession(String(savedCommand.session_id));
      if(sessionAfter && (!sessionAfter.enabled || sessionAfter.status!=="connected")){
        await releaseAndroidSessionLease(String(savedCommand.session_id));
      }
    }
    return new Response(JSON.stringify({ok:true}),{status:200,headers:{...corsHeaders,"Content-Type":"application/json"}});
  }


  if(!["POST","GET","DELETE"].includes(req.method)) return new Response("Method not allowed",{status:405,headers:corsHeaders});
  const server=createServer();
  const transport=new WebStandardStreamableHTTPServerTransport({sessionIdGenerator:undefined,enableJsonResponse:true});
  try{
    await server.connect(transport);
    const response=await transport.handleRequest(req);
    const headers=new Headers(response.headers);
    Object.entries(corsHeaders).forEach(([k,v])=>headers.set(k,v));
    return new Response(response.body,{status:response.status,statusText:response.statusText,headers});
  }catch(error){
    console.error("ANDROID_SESSION_BRIDGE_MCP_FAILED",error?.message||String(error));
    try{await transport.close()}catch{}
    try{await server.close()}catch{}
    return new Response("Internal server error",{status:500,headers:corsHeaders});
  }
});
