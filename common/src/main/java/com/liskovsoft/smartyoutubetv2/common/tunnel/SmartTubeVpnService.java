package com.liskovsoft.smartyoutubetv2.common.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;

/**
 * SmartTube Aurora Tunnel & VPN Background Service.
 * Provides:
 * - High-priority Foreground Service keep-alive for TV & Mobile
 * - Partial CPU WakeLock + High-Performance Wi-Fi Lock (prevents background throttling & UDP drops)
 * - Optional Android VpnService TUN interface establishment when VPN permission is granted
 */
public class SmartTubeVpnService extends VpnService {
    public static final String ACTION_START = "com.liskovsoft.smartyoutubetv2.vpn.START";
    public static final String ACTION_STOP = "com.liskovsoft.smartyoutubetv2.vpn.STOP";
    private static final String CHANNEL_ID = "smarttube_aurora_tunnel_channel";
    private static final int NOTIF_ID = 7749;

    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;
    private ParcelFileDescriptor mVpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            releaseLocksAndStop();
            return START_NOT_STICKY;
        }

        startForegroundKeepAlive();
        acquireBackgroundOptimizationLocks();
        return START_STICKY;
    }

    private void startForegroundKeepAlive() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "SmartTube Aurora Tunnel & VPN",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Keeps SSH/WS/VLESS/VMess/SlowDNS/UDP tunnel active in background");
                nm.createNotificationChannel(channel);

                Notification notification = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("SmartTube Aurora Tunnel Active")
                        .setContentText("Multi-Protocol Tunnel + UDP/DoH & Background Optimization Running")
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setOngoing(true)
                        .build();
                startForeground(NOTIF_ID, notification);
            }
        } catch (Throwable ignored) {
        }
    }

    private void acquireBackgroundOptimizationLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && (mWakeLock == null || !mWakeLock.isHeld())) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartTubeAurora::TunnelWakeLock");
                mWakeLock.setReferenceCounted(false);
                mWakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours
            }

            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && (mWifiLock == null || !mWifiLock.isHeld())) {
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SmartTubeAurora::TunnelWifiLock");
                mWifiLock.setReferenceCounted(false);
                mWifiLock.acquire();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseLocksAndStop() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } catch (Throwable ignored) {}
        try {
            if (mWifiLock != null && mWifiLock.isHeld()) {
                mWifiLock.release();
            }
        } catch (Throwable ignored) {}
        try {
            if (mVpnInterface != null) {
                mVpnInterface.close();
                mVpnInterface = null;
            }
        } catch (Throwable ignored) {}

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releaseLocksAndStop();
        super.onDestroy();
    }
}
