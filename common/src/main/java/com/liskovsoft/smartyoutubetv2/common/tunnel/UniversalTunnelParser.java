package com.liskovsoft.smartyoutubetv2.common.tunnel;

import android.net.Uri;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal Tunnel Configuration Parser.
 * Supports:
 * 1. Habibi Dark Tunnel (.dark.txt / JSON with encryptedLockedConfig)
 * 2. Habibi x NullptrO NetMod (.nm.txt / JSON with config & summary)
 * 3. VLESS (vless://...)
 * 4. VMess (vmess:// base64 JSON)
 * 5. SSH / WebSocket / SSL (ssh://, ws://, wss://)
 * 6. SlowDNS / DNSTT (sdns://, dnstt://, dns://)
 * 7. UDP / Hysteria2 / BadVPN (udp://, hy2://, hysteria2://)
 * 8. DNS-over-HTTPS (doh://, https://.../dns-query)
 * 9. Generic Payload/SNI/SSH key-value .txt configs
 */
public class UniversalTunnelParser {

    public static List<TunnelConfig> parseTextContent(String rawText, String sourceFileName) {
        List<TunnelConfig> results = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return results;
        }

        String baseName = sourceFileName != null ? sourceFileName.replaceFirst("\\.(dark\\.txt|nm\\.txt|txt|dark|nm)$", "").trim() : "Imported Config";

        // 1. Extract any JSON objects embedded inside script banners (e.g. HABIBI DARK TUNNEL SCRIPT / NETMOD SCRIPT)
        List<String> jsonBlocks = extractJsonObjects(rawText);
        for (String jsonStr : jsonBlocks) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("encryptedLockedConfig") || json.has("sshTunnelConfig") || "SSH".equalsIgnoreCase(json.optString("type"))) {
                    TunnelConfig darkCfg = parseDarkTunnelJson(json, baseName, sourceFileName);
                    if (darkCfg != null) {
                        results.add(darkCfg);
                    }
                } else if (json.has("config") || json.has("summary") || "config".equalsIgnoreCase(json.optString("type"))) {
                    List<TunnelConfig> nmConfigs = parseNetModJson(json, baseName, sourceFileName);
                    results.addAll(nmConfigs);
                } else if (json.has("outbounds") || json.has("inbounds")) {
                    TunnelConfig v2Cfg = parseV2rayFullJson(json, baseName, sourceFileName);
                    if (v2Cfg != null) {
                        results.add(v2Cfg);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Scan for standard URI schemes (vless://, vmess://, ssh://, ws://, wss://, sdns://, dnstt://, udp://, hy2://, doh://, socks5://)
        String[] lines = rawText.split("\\r?\\n");
        int uriIndex = 1;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith("=")) {
                continue;
            }
            TunnelConfig uriCfg = parseUriLine(line, baseName + (lines.length > 1 ? " #" + uriIndex : ""), sourceFileName);
            if (uriCfg != null) {
                results.add(uriCfg);
                uriIndex++;
            }
        }

        // 3. Fallback: If rawText has HTTP Payload / Host / SSH lines in plain text format
        if (results.isEmpty() && (rawText.contains("[crlf]") || rawText.toLowerCase().contains("upgrade: websocket") || rawText.toLowerCase().contains("cloudfront.net"))) {
            TunnelConfig fallback = parsePlainPayloadScript(rawText, baseName, sourceFileName);
            if (fallback != null) {
                results.add(fallback);
            }
        }

        return results;
    }

    private static TunnelConfig parseDarkTunnelJson(JSONObject root, String defaultName, String fileName) {
        TunnelConfig cfg = new TunnelConfig();
        cfg.fileName = fileName != null ? fileName : "";
        cfg.sourceFormat = "DARK_TUNNEL";
        String rawName = root.optString("name", "").trim();
        cfg.name = !rawName.isEmpty() ? cleanDisplayTitle(rawName) : defaultName;

        JSONObject encWrapper = root.optJSONObject("encryptedLockedConfig");
        if (encWrapper != null) {
            JSONObject lockedApp = encWrapper.optJSONObject("LockedAppConfig");
            if (lockedApp != null) {
                cfg.bannerMessageHtml = lockedApp.optString("Message", "");
                cfg.connectedMessageHtml = lockedApp.optString("ConnectedMessage", "");
                cfg.expiredAtTimestamp = lockedApp.optLong("ExpiredAtTimestamp", 0L);
            }

            JSONObject encCfg = encWrapper.optJSONObject("EncryptedLockedConfig");
            if (encCfg != null) {
                JSONObject sshCfg = encCfg.optJSONObject("SshConfig");
                if (sshCfg != null) {
                    cfg.sshHost = sshCfg.optString("EncryptedHost", "").trim();
                    cfg.sshPort = parsePort(sshCfg.optString("EncryptedPort", "80"), 80);
                    cfg.sshUsername = sshCfg.optString("EncryptedUsername", "").trim();
                    cfg.sshPassword = sshCfg.optString("EncryptedPassword", "").trim();
                }

                JSONObject injectCfg = encCfg.optJSONObject("InjectConfig");
                if (injectCfg != null) {
                    cfg.proxyHost = injectCfg.optString("EncryptedProxyHost", "localhost").trim();
                    cfg.proxyPort = parsePort(injectCfg.optString("EncryptedProxyPort", "8080"), 8080);
                    cfg.wsPayload = cleanHtmlFromPayload(injectCfg.optString("EncryptedPayload", ""));
                    cfg.dnsttDnsHost = injectCfg.optString("EncryptedDnsttDnsHost", "1.1.1.1").trim();
                    cfg.dnsttDnsPort = parsePort(injectCfg.optString("EncryptedDnsttDnsPort", "53"), 53);

                    JSONArray sniArr = injectCfg.optJSONArray("EncryptedServerNameIndication");
                    if (sniArr != null && sniArr.length() > 0) {
                        cfg.sniHost = sniArr.optString(0, "");
                    }
                    JSONArray dnsNameArr = injectCfg.optJSONArray("EncryptedDnsttServerName");
                    if (dnsNameArr != null && dnsNameArr.length() > 0) {
                        cfg.dnsttServerName = dnsNameArr.optString(0, "");
                    }
                    JSONArray pubKeyArr = injectCfg.optJSONArray("EncryptedDnsttPubkey");
                    if (pubKeyArr != null && pubKeyArr.length() > 0) {
                        cfg.dnsttPubkey = pubKeyArr.optString(0, "");
                    }
                }

                JSONObject v2rayCfg = encCfg.optJSONObject("V2RayConfig");
                if (v2rayCfg != null && v2rayCfg.optBoolean("IsInjectModeEnabled", false)) {
                    JSONArray v2Arr = v2rayCfg.optJSONArray("EncryptedConfig");
                    if (v2Arr != null && v2Arr.length() > 0) {
                        cfg.rawV2rayJson = v2Arr.optString(0, "");
                        cfg.protocol = "VLESS/VMess+WS";
                    }
                }
            }
        }

        if (cfg.sniHost == null || cfg.sniHost.isEmpty()) {
            cfg.sniHost = extractHostFromPayload(cfg.wsPayload);
        }

        if (cfg.dnsttPubkey != null && !cfg.dnsttPubkey.isEmpty()) {
            cfg.protocol = "SSH+SlowDNS+WS";
        } else if (cfg.wsPayload != null && cfg.wsPayload.toLowerCase().contains("websocket")) {
            cfg.protocol = "SSH+WS+CDN";
        } else {
            cfg.protocol = "SSH+DIRECT";
        }

        return cfg;
    }

    private static List<TunnelConfig> parseNetModJson(JSONObject root, String defaultName, String fileName) {
        List<TunnelConfig> list = new ArrayList<>();
        JSONObject configObj = root.optJSONObject("config");
        JSONObject summaryObj = root.optJSONObject("summary");

        String payload = "";
        String sni = "";
        String note = "";

        if (configObj != null) {
            JSONObject payloadObj = configObj.optJSONObject("Payload");
            if (payloadObj != null) {
                payload = payloadObj.optString("Value", "");
            }
            if (payload.isEmpty()) {
                JSONObject rrObj = configObj.optJSONObject("RR");
                if (rrObj != null) {
                    payload = rrObj.optString("Manual", "");
                }
            }
            JSONObject sniObj = configObj.optJSONObject("SNI");
            if (sniObj != null) {
                sni = sniObj.optString("Value", "");
            }
            note = configObj.optString("Note", "");
        }

        if (summaryObj != null) {
            if (payload.isEmpty()) payload = summaryObj.optString("payload", "");
            if (sni.isEmpty()) sni = summaryObj.optString("sni", "");
        }

        String cleanPayload = cleanHtmlFromPayload(payload);
        if (sni.isEmpty()) {
            sni = extractHostFromPayload(cleanPayload);
        }

        JSONArray sshArr = null;
        if (configObj != null && configObj.has("SSH")) {
            sshArr = configObj.optJSONArray("SSH");
        } else if (summaryObj != null && summaryObj.has("ssh")) {
            sshArr = summaryObj.optJSONArray("ssh");
        }

        if (sshArr != null && sshArr.length() > 0) {
            for (int i = 0; i < sshArr.length(); i++) {
                JSONObject s = sshArr.optJSONObject(i);
                if (s == null) continue;
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "NETMOD";
                String remark = s.optString("Remark", s.optString("remark", "")).trim();
                cfg.name = !note.isEmpty() ? note + (!remark.isEmpty() ? " (" + remark + ")" : "") : (!remark.isEmpty() ? defaultName + " - " + remark : defaultName);
                cfg.sshHost = s.optString("Host", s.optString("host", "")).trim();
                cfg.sshPort = parsePort(s.optString("Port", s.optString("port", "443")), 443);
                cfg.sshUsername = s.optString("User", s.optString("username", "")).trim();
                cfg.sshPassword = s.optString("Pass", s.optString("password", "")).trim();
                cfg.wsPayload = cleanPayload;
                cfg.sniHost = sni;
                cfg.bannerMessageHtml = !note.isEmpty() ? "<b>NetMod Profile:</b> " + note : "";
                cfg.protocol = (cfg.sshPort == 443 || !cfg.sniHost.isEmpty()) ? "SSH+WS+TLS (NetMod)" : "SSH+WS (NetMod)";
                list.add(cfg);
            }
        } else {
            TunnelConfig cfg = new TunnelConfig();
            cfg.fileName = fileName != null ? fileName : "";
            cfg.sourceFormat = "NETMOD";
            cfg.name = !note.isEmpty() ? note : defaultName;
            cfg.wsPayload = cleanPayload;
            cfg.sniHost = sni;
            cfg.protocol = "WS+SNI (NetMod)";
            list.add(cfg);
        }

        return list;
    }

    private static TunnelConfig parseV2rayFullJson(JSONObject json, String baseName, String fileName) {
        TunnelConfig cfg = new TunnelConfig();
        cfg.fileName = fileName != null ? fileName : "";
        cfg.sourceFormat = "V2RAY_JSON";
        cfg.name = baseName + " (V2Ray/Xray)";
        cfg.protocol = "VLESS/VMess+WS";
        cfg.rawV2rayJson = json.toString();
        try {
            JSONArray outbounds = json.optJSONArray("outbounds");
            if (outbounds != null && outbounds.length() > 0) {
                JSONObject first = outbounds.getJSONObject(0);
                String proto = first.optString("protocol", "vless").toUpperCase();
                cfg.protocol = proto + "+WS";
                JSONObject settings = first.optJSONObject("settings");
                if (settings != null) {
                    JSONArray vnext = settings.optJSONArray("vnext");
                    if (vnext != null && vnext.length() > 0) {
                        JSONObject srv = vnext.getJSONObject(0);
                        cfg.sshHost = srv.optString("address", "");
                        cfg.sshPort = srv.optInt("port", 443);
                        JSONArray users = srv.optJSONArray("users");
                        if (users != null && users.length() > 0) {
                            cfg.v2rayUuid = users.getJSONObject(0).optString("id", "");
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return cfg;
    }

    private static TunnelConfig parseUriLine(String line, String defaultName, String fileName) {
        String lower = line.toLowerCase();
        try {
            if (lower.startsWith("vless://")) {
                Uri uri = Uri.parse(line);
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "VLESS";
                cfg.protocol = "VLESS+WS+TLS";
                cfg.v2rayUuid = uri.getUserInfo() != null ? uri.getUserInfo() : "";
                cfg.sshHost = uri.getHost() != null ? uri.getHost() : "";
                cfg.sshPort = uri.getPort() > 0 ? uri.getPort() : 443;
                cfg.sniHost = uri.getQueryParameter("sni") != null ? uri.getQueryParameter("sni") : (uri.getQueryParameter("host") != null ? uri.getQueryParameter("host") : cfg.sshHost);
                cfg.v2rayPath = uri.getQueryParameter("path") != null ? uri.getQueryParameter("path") : "/";
                cfg.v2rayNetwork = uri.getQueryParameter("type") != null ? uri.getQueryParameter("type") : "ws";
                cfg.v2raySecurity = uri.getQueryParameter("security") != null ? uri.getQueryParameter("security") : "tls";
                String frag = uri.getFragment();
                cfg.name = (frag != null && !frag.isEmpty()) ? Uri.decode(frag) : defaultName + " (VLESS)";
                cfg.wsPayload = "GET " + cfg.v2rayPath + " HTTP/1.1[crlf]Host: " + cfg.sniHost + "[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]";
                return cfg;
            } else if (lower.startsWith("vmess://")) {
                String b64 = line.substring("vmess://".length()).trim();
                byte[] decoded = Base64.decode(b64, Base64.DEFAULT);
                JSONObject obj = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "VMESS";
                cfg.protocol = "VMess+" + obj.optString("net", "ws").toUpperCase();
                cfg.name = obj.optString("ps", defaultName + " (VMess)");
                cfg.sshHost = obj.optString("add", "");
                cfg.sshPort = parsePort(obj.optString("port", "443"), 443);
                cfg.v2rayUuid = obj.optString("id", "");
                cfg.sniHost = obj.optString("host", obj.optString("sni", cfg.sshHost));
                cfg.v2rayPath = obj.optString("path", "/");
                cfg.v2raySecurity = obj.optString("tls", "tls");
                cfg.wsPayload = "GET " + cfg.v2rayPath + " HTTP/1.1[crlf]Host: " + cfg.sniHost + "[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]";
                return cfg;
            } else if (lower.startsWith("ssh://") || lower.startsWith("ws://") || lower.startsWith("wss://")) {
                Uri uri = Uri.parse(line);
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "SSH_WS";
                cfg.protocol = lower.startsWith("wss://") ? "WSS+TLS" : (lower.startsWith("ws://") ? "WebSocket" : "SSH+WS");
                cfg.sshHost = uri.getHost() != null ? uri.getHost() : "";
                cfg.sshPort = uri.getPort() > 0 ? uri.getPort() : (lower.startsWith("wss://") ? 443 : 80);
                if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
                    String[] up = uri.getUserInfo().split(":", 2);
                    cfg.sshUsername = up[0];
                    cfg.sshPassword = up[1];
                }
                cfg.sniHost = uri.getQueryParameter("sni") != null ? uri.getQueryParameter("sni") : cfg.sshHost;
                String frag = uri.getFragment();
                cfg.name = (frag != null && !frag.isEmpty()) ? Uri.decode(frag) : defaultName + " (" + cfg.protocol + ")";
                return cfg;
            } else if (lower.startsWith("sdns://") || lower.startsWith("dnstt://") || lower.startsWith("slowdns://")) {
                Uri uri = Uri.parse(line);
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "SLOWDNS";
                cfg.protocol = "SlowDNS (DNSTT)";
                cfg.dnsttDnsHost = uri.getHost() != null ? uri.getHost() : "1.1.1.1";
                cfg.dnsttDnsPort = uri.getPort() > 0 ? uri.getPort() : 53;
                cfg.dnsttServerName = uri.getQueryParameter("ns") != null ? uri.getQueryParameter("ns") : "";
                cfg.dnsttPubkey = uri.getQueryParameter("pubkey") != null ? uri.getQueryParameter("pubkey") : "";
                cfg.sshHost = cfg.dnsttDnsHost;
                cfg.sshPort = cfg.dnsttDnsPort;
                String frag = uri.getFragment();
                cfg.name = (frag != null && !frag.isEmpty()) ? Uri.decode(frag) : defaultName + " (SlowDNS)";
                return cfg;
            } else if (lower.startsWith("udp://") || lower.startsWith("hy2://") || lower.startsWith("hysteria2://")) {
                Uri uri = Uri.parse(line);
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "UDP";
                cfg.protocol = lower.startsWith("udp://") ? "UDP Custom / BadVPN" : "UDP Hysteria2";
                cfg.sshHost = uri.getHost() != null ? uri.getHost() : "";
                cfg.sshPort = uri.getPort() > 0 ? uri.getPort() : 7300;
                cfg.udpGwPort = cfg.sshPort;
                cfg.enableUdp = true;
                String frag = uri.getFragment();
                cfg.name = (frag != null && !frag.isEmpty()) ? Uri.decode(frag) : defaultName + " (UDP)";
                return cfg;
            } else if (lower.startsWith("doh://") || (lower.startsWith("https://") && lower.contains("/dns-query"))) {
                TunnelConfig cfg = new TunnelConfig();
                cfg.fileName = fileName != null ? fileName : "";
                cfg.sourceFormat = "DOH";
                cfg.protocol = "DoH (DNS-over-HTTPS)";
                cfg.dohUrl = line.replaceFirst("^doh://", "https://");
                Uri uri = Uri.parse(cfg.dohUrl);
                cfg.sshHost = uri.getHost() != null ? uri.getHost() : "1.1.1.1";
                cfg.sshPort = 443;
                cfg.enableDoh = true;
                cfg.name = defaultName + " (DoH: " + cfg.sshHost + ")";
                return cfg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static TunnelConfig parsePlainPayloadScript(String rawText, String defaultName, String fileName) {
        TunnelConfig cfg = new TunnelConfig();
        cfg.fileName = fileName != null ? fileName : "";
        cfg.sourceFormat = "CUSTOM_SCRIPT";
        cfg.name = defaultName;
        cfg.wsPayload = cleanHtmlFromPayload(rawText.trim());
        cfg.sniHost = extractHostFromPayload(cfg.wsPayload);
        cfg.sshHost = !cfg.sniHost.isEmpty() ? cfg.sniHost : "143.204.106.9";
        cfg.sshPort = 80;
        cfg.protocol = "HTTP+WS Payload";
        return cfg;
    }

    public static List<String> extractJsonObjects(String text) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inQuotes = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        list.add(text.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return list;
    }

    public static String cleanHtmlFromPayload(String payload) {
        if (payload == null) return "";
        // NetMod payloads sometimes embed a decorative <div><span>...</span></div> inside Connection: Upgrade
        String cleaned = payload.replaceAll("<div[^>]*>.*?</div>", " ");
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        cleaned = cleaned.replaceAll("Connection:\\s+Upgrade", "Connection: Upgrade");
        return cleaned.trim();
    }

    public static String extractHostFromPayload(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        Pattern p = Pattern.compile("Host:\\s*([^\\s\\[\\r\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(payload);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String cleanDisplayTitle(String raw) {
        // Strip garbled Mojibake while keeping valid emojis and ASCII/Unicode letters
        return raw.replaceAll("[\\uFFFD\uE000-\uF8FF]", "").trim();
    }

    private static int parsePort(String str, int fallback) {
        try {
            int p = Integer.parseInt(str.trim());
            return (p > 0 && p <= 65535) ? p : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
