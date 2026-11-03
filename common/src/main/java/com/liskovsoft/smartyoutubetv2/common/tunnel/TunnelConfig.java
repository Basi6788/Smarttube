package com.liskovsoft.smartyoutubetv2.common.tunnel;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class TunnelConfig {
    public String id;
    public String name;
    public String fileName;
    public String sourceFormat; // DARK_TUNNEL, NETMOD, VLESS, VMESS, SSH_WS, SLOWDNS, UDP, DOH, CUSTOM
    public String protocol;     // SSH+WS, VLESS+WS, VMESS+WS, SLOWDNS, UDP, DoH, SSH+SSL, SOCKS5, HTTP

    // SSH / Server settings
    public String sshHost = "";
    public int sshPort = 80;
    public String sshUsername = "";
    public String sshPassword = "";

    // Payload & SNI / WebSocket settings
    public String sniHost = "";
    public String wsPayload = "";
    public String proxyHost = "";
    public int proxyPort = 8080;

    // SlowDNS / DNSTT / DoH / UDP settings
    public String dnsttDnsHost = "1.1.1.1";
    public int dnsttDnsPort = 53;
    public String dnsttServerName = "";
    public String dnsttPubkey = "";
    public String dohUrl = "https://1.1.1.1/dns-query";
    public int udpGwPort = 7300;
    public boolean enableUdp = true;
    public boolean enableDoh = true;

    // VLESS / VMess / V2Ray fields
    public String v2rayUuid = "";
    public String v2rayPath = "/";
    public String v2raySecurity = "tls";
    public String v2rayNetwork = "ws";
    public String rawV2rayJson = "";

    // Config Metadata & Messages
    public String bannerMessageHtml = "";
    public String connectedMessageHtml = "";
    public long expiredAtTimestamp = 0;

    public TunnelConfig() {
        this.id = UUID.randomUUID().toString();
    }

    public String getSummaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(protocol != null && !protocol.isEmpty() ? protocol : "TUNNEL").append("] ");
        if (sshHost != null && !sshHost.isEmpty()) {
            sb.append(sshHost).append(":").append(sshPort);
        }
        if (sniHost != null && !sniHost.isEmpty()) {
            sb.append(" • SNI: ").append(sniHost);
        } else if (wsPayload != null && wsPayload.contains("Host:")) {
            sb.append(" • WS Payload");
        }
        if (enableUdp) {
            sb.append(" • UDP:").append(udpGwPort);
        }
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("fileName", fileName);
            obj.put("sourceFormat", sourceFormat);
            obj.put("protocol", protocol);
            obj.put("sshHost", sshHost);
            obj.put("sshPort", sshPort);
            obj.put("sshUsername", sshUsername);
            obj.put("sshPassword", sshPassword);
            obj.put("sniHost", sniHost);
            obj.put("wsPayload", wsPayload);
            obj.put("proxyHost", proxyHost);
            obj.put("proxyPort", proxyPort);
            obj.put("dnsttDnsHost", dnsttDnsHost);
            obj.put("dnsttDnsPort", dnsttDnsPort);
            obj.put("dnsttServerName", dnsttServerName);
            obj.put("dnsttPubkey", dnsttPubkey);
            obj.put("dohUrl", dohUrl);
            obj.put("udpGwPort", udpGwPort);
            obj.put("enableUdp", enableUdp);
            obj.put("enableDoh", enableDoh);
            obj.put("v2rayUuid", v2rayUuid);
            obj.put("v2rayPath", v2rayPath);
            obj.put("v2raySecurity", v2raySecurity);
            obj.put("v2rayNetwork", v2rayNetwork);
            obj.put("rawV2rayJson", rawV2rayJson);
            obj.put("bannerMessageHtml", bannerMessageHtml);
            obj.put("connectedMessageHtml", connectedMessageHtml);
            obj.put("expiredAtTimestamp", expiredAtTimestamp);
        } catch (JSONException ignored) {
        }
        return obj;
    }

    public static TunnelConfig fromJson(JSONObject obj) {
        TunnelConfig cfg = new TunnelConfig();
        if (obj == null) return cfg;
        cfg.id = obj.optString("id", UUID.randomUUID().toString());
        cfg.name = obj.optString("name", "Imported Tunnel");
        cfg.fileName = obj.optString("fileName", "");
        cfg.sourceFormat = obj.optString("sourceFormat", "CUSTOM");
        cfg.protocol = obj.optString("protocol", "SSH+WS");
        cfg.sshHost = obj.optString("sshHost", "");
        cfg.sshPort = obj.optInt("sshPort", 80);
        cfg.sshUsername = obj.optString("sshUsername", "");
        cfg.sshPassword = obj.optString("sshPassword", "");
        cfg.sniHost = obj.optString("sniHost", "");
        cfg.wsPayload = obj.optString("wsPayload", "");
        cfg.proxyHost = obj.optString("proxyHost", "");
        cfg.proxyPort = obj.optInt("proxyPort", 8080);
        cfg.dnsttDnsHost = obj.optString("dnsttDnsHost", "1.1.1.1");
        cfg.dnsttDnsPort = obj.optInt("dnsttDnsPort", 53);
        cfg.dnsttServerName = obj.optString("dnsttServerName", "");
        cfg.dnsttPubkey = obj.optString("dnsttPubkey", "");
        cfg.dohUrl = obj.optString("dohUrl", "https://1.1.1.1/dns-query");
        cfg.udpGwPort = obj.optInt("udpGwPort", 7300);
        cfg.enableUdp = obj.optBoolean("enableUdp", true);
        cfg.enableDoh = obj.optBoolean("enableDoh", true);
        cfg.v2rayUuid = obj.optString("v2rayUuid", "");
        cfg.v2rayPath = obj.optString("v2rayPath", "/");
        cfg.v2raySecurity = obj.optString("v2raySecurity", "tls");
        cfg.v2rayNetwork = obj.optString("v2rayNetwork", "ws");
        cfg.rawV2rayJson = obj.optString("rawV2rayJson", "");
        cfg.bannerMessageHtml = obj.optString("bannerMessageHtml", "");
        cfg.connectedMessageHtml = obj.optString("connectedMessageHtml", "");
        cfg.expiredAtTimestamp = obj.optLong("expiredAtTimestamp", 0);
        return cfg;
    }
}
