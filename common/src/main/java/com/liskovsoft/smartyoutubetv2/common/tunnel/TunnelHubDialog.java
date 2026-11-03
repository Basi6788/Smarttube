package com.liskovsoft.smartyoutubetv2.common.tunnel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import arte.programar.materialfile.MaterialFilePicker;
import arte.programar.materialfile.ui.FilePickerActivity;

/**
 * Interactive Material 3 Expressive Aurora Dialog for managing Tunnel Configs (.txt / .dark / .nm),
 * connecting/disconnecting VPN/Proxy tunnels, viewing real-time logs on TV & Mobile,
 * and running the live YouTube Thumbnail & Data Connectivity Test.
 */
public class TunnelHubDialog implements MotherActivity.OnResult, TunnelEngineManager.TunnelLogListener, TunnelEngineManager.TunnelStateListener {
    private static final int REQ_SAF_PICK_TXT = 7801;
    private static final int REQ_MATERIAL_PICK_TXT = 7802;

    private final Context mContext;
    private final TunnelEngineManager mEngine;
    private AlertDialog mDialog;

    private TextView mStatusPill;
    private TextView mSelectedName;
    private TextView mSelectedDetails;
    private TextView mSelectedBanner;
    private Button mBtnConnect;
    private TextView mLogsView;
    private ScrollView mLogsScroll;
    private ImageView mTestThumbnail;
    private TextView mTestResult;
    private TextView mTestSubtext;

    public TunnelHubDialog(Context context) {
        mContext = context;
        mEngine = TunnelEngineManager.instance(context);
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AppDialog);
        View root = LayoutInflater.from(mContext).inflate(R.layout.tunnel_hub_dialog, null);

        mStatusPill = root.findViewById(R.id.tv_tunnel_status_pill);
        mSelectedName = root.findViewById(R.id.tv_selected_config_name);
        mSelectedDetails = root.findViewById(R.id.tv_selected_config_details);
        mSelectedBanner = root.findViewById(R.id.tv_selected_config_banner);
        mBtnConnect = root.findViewById(R.id.btn_connect_tunnel);
        mLogsView = root.findViewById(R.id.tv_tunnel_logs);
        mLogsScroll = root.findViewById(R.id.sv_tunnel_logs);
        mTestThumbnail = root.findViewById(R.id.iv_test_thumbnail);
        mTestResult = root.findViewById(R.id.tv_test_result);
        mTestSubtext = root.findViewById(R.id.tv_test_subtext);

        Button btnSwitch = root.findViewById(R.id.btn_switch_config);
        Button btnImportTxt = root.findViewById(R.id.btn_import_txt_file);
        Button btnScanStorage = root.findViewById(R.id.btn_scan_storage);
        Button btnPaste = root.findViewById(R.id.btn_paste_config);
        Button btnClearLogs = root.findViewById(R.id.btn_clear_logs);
        Button btnTest = root.findViewById(R.id.btn_test_connection);
        Button btnClose = root.findViewById(R.id.btn_close_tunnel_hub);
        CheckBox cbBgOptimize = root.findViewById(R.id.cb_bg_optimize);

        cbBgOptimize.setChecked(mEngine.isBgOptimizeEnabled());
        cbBgOptimize.setOnCheckedChangeListener((buttonView, isChecked) -> mEngine.setBgOptimizeEnabled(isChecked));

        btnSwitch.setOnClickListener(v -> showConfigSelectorDialog());
        mBtnConnect.setOnClickListener(v -> mEngine.toggleConnect());
        btnImportTxt.setOnClickListener(v -> openTxtFileImporter());
        btnScanStorage.setOnClickListener(v -> {
            PermissionHelpers.verifyStoragePermissions(mContext);
            int count = mEngine.scanAndImportStorageConfigs();
            refreshConfigHeader();
            MessageHelpers.showMessage(mContext, "Imported " + count + " config(s) from storage. Total: " + mEngine.getConfigs().size());
        });
        btnPaste.setOnClickListener(v -> showPasteConfigDialog());
        btnClearLogs.setOnClickListener(v -> mEngine.clearLogs());
        btnTest.setOnClickListener(v -> triggerThumbnailConnectivityTest());
        btnClose.setOnClickListener(v -> {
            if (mDialog != null) mDialog.dismiss();
        });

        mEngine.addLogListener(this);
        mEngine.addStateListener(this);

        refreshConfigHeader();
        refreshConnectionUi(mEngine.isConnected(), mEngine.getSelectedConfig());
        renderLogs(mEngine.getLogs());

        mDialog = builder.setView(root).create();
        mDialog.setOnDismissListener(d -> {
            mEngine.removeLogListener(this);
            mEngine.removeStateListener(this);
        });
        mDialog.show();
    }

    private void refreshConfigHeader() {
        TunnelConfig cfg = mEngine.getSelectedConfig();
        if (cfg == null) {
            mSelectedName.setText("No Config Selected (Import a .txt file)");
            mSelectedDetails.setText("Supports .dark.txt, .nm.txt, VLESS, VMess, SSH, WS, SlowDNS, DoH, UDP");
            mSelectedBanner.setVisibility(View.GONE);
            return;
        }
        mSelectedName.setText("Selected (" + mEngine.getConfigs().size() + " loaded): " + cfg.name);
        mSelectedDetails.setText(cfg.getSummaryLine());
        if (cfg.bannerMessageHtml != null && !cfg.bannerMessageHtml.isEmpty()) {
            mSelectedBanner.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= 24) {
                mSelectedBanner.setText(Html.fromHtml(cfg.bannerMessageHtml, Html.FROM_HTML_MODE_COMPACT));
            } else {
                mSelectedBanner.setText(Html.fromHtml(cfg.bannerMessageHtml));
            }
        } else {
            mSelectedBanner.setVisibility(View.GONE);
        }
    }

    private void refreshConnectionUi(boolean connected, TunnelConfig cfg) {
        if (connected) {
            mStatusPill.setText("● CONNECTED (" + (cfg != null ? cfg.protocol : "TUNNEL") + ")");
            mStatusPill.setTextColor(Color.parseColor("#00FF87"));
            mBtnConnect.setText("⛔ DISCONNECT TUNNEL");
        } else {
            mStatusPill.setText("● READY TO CONNECT");
            mStatusPill.setTextColor(Color.parseColor("#00F2FE"));
            mBtnConnect.setText("🚀 CONNECT TUNNEL");
        }
    }

    private void showConfigSelectorDialog() {
        List<TunnelConfig> configs = mEngine.getConfigs();
        if (configs.isEmpty()) {
            MessageHelpers.showMessage(mContext, "No configs available. Import a .txt file first.");
            return;
        }
        String[] titles = new String[configs.size()];
        int checkedIdx = 0;
        TunnelConfig current = mEngine.getSelectedConfig();
        for (int i = 0; i < configs.size(); i++) {
            TunnelConfig c = configs.get(i);
            titles[i] = c.name + "\n" + c.getSummaryLine();
            if (current != null && c.id.equals(current.id)) {
                checkedIdx = i;
            }
        }

        new AlertDialog.Builder(mContext, R.style.AppDialog)
                .setTitle("Select Tunnel Configuration (" + configs.size() + ")")
                .setSingleChoiceItems(titles, checkedIdx, (dialog, which) -> {
                    TunnelConfig chosen = configs.get(which);
                    mEngine.setSelectedConfigId(chosen.id);
                    mEngine.appendLog("SELECT", "Switched active profile to: " + chosen.name + " (" + chosen.getSummaryLine() + ")");
                    refreshConfigHeader();
                    dialog.dismiss();
                    if (mEngine.isConnected()) {
                        mEngine.connectSelected();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openTxtFileImporter() {
        PermissionHelpers.verifyStoragePermissions(mContext);
        if (mContext instanceof MotherActivity) {
            ((MotherActivity) mContext).addOnResult(this);
        }

        // Show chooser between Android System File Picker (SAF) and Built-in TV/Mobile Material Folder Browser
        String[] modes = new String[]{
                "📱 System File Picker (.txt / .dark / .nm from Downloads/USB)",
                "📺 Built-in TV & Mobile Storage Browser (/sdcard/)",
                "⚡ Auto-Scan All Storage Folders for .txt Configs"
        };

        new AlertDialog.Builder(mContext, R.style.AppDialog)
                .setTitle("Import Tunnel Config (.txt)")
                .setItems(modes, (d, which) -> {
                    if (which == 0) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            if (mContext instanceof Activity) {
                                ((Activity) mContext).startActivityForResult(intent, REQ_SAF_PICK_TXT);
                            }
                        } catch (Exception e) {
                            launchBuiltInFilePicker();
                        }
                    } else if (which == 1) {
                        launchBuiltInFilePicker();
                    } else {
                        int count = mEngine.scanAndImportStorageConfigs();
                        refreshConfigHeader();
                        MessageHelpers.showMessage(mContext, "Auto-imported " + count + " config(s)!");
                    }
                })
                .show();
    }

    private void launchBuiltInFilePicker() {
        if (mContext instanceof Activity) {
            try {
                new MaterialFilePicker()
                        .withActivity((Activity) mContext)
                        .withFilter(Pattern.compile(".*\\.(txt|dark|nm|json|conf)$", Pattern.CASE_INSENSITIVE))
                        .withHiddenFiles(true)
                        .withTitle("Select Tunnel .txt Config File")
                        .start(REQ_MATERIAL_PICK_TXT);
            } catch (Exception e) {
                MessageHelpers.showMessage(mContext, "File browser error: " + e.getMessage());
            }
        }
    }

    private void showPasteConfigDialog() {
        EditText input = new EditText(mContext);
        input.setHint("Paste .dark.txt / .nm.txt script, vless://, vmess://, ssh://, sdns://, udp://, doh://, or Payload...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setMinLines(4);
        input.setMaxLines(10);

        new AlertDialog.Builder(mContext, R.style.AppDialog)
                .setTitle("Paste Tunnel Config / URI")
                .setView(input)
                .setPositiveButton("Import & Add", (d, w) -> {
                    String raw = input.getText().toString().trim();
                    if (!raw.isEmpty()) {
                        int added = mEngine.importRawText(raw, "Custom_Pasted_Config.txt");
                        refreshConfigHeader();
                        MessageHelpers.showMessage(mContext, "Added " + added + " config(s)!");
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void triggerThumbnailConnectivityTest() {
        mTestResult.setText("⏳ Testing connection & fetching live YouTube thumbnail...");
        mTestResult.setTextColor(Color.parseColor("#00F2FE"));
        mTestSubtext.setText("Connecting through active tunnel & verifying HTTP 200 image stream...");

        mEngine.runConnectivityAndThumbnailTest((success, thumbnail, latencyMs, sizeKb, httpCode, summary) -> {
            mTestResult.setText(summary);
            if (success) {
                mTestResult.setTextColor(Color.parseColor("#00FF87"));
                mTestSubtext.setText(String.format("Verified: %.1f KB thumbnail downloaded in %d ms (HTTP %d)", sizeKb, latencyMs, httpCode));
                if (thumbnail != null) {
                    mTestThumbnail.setImageBitmap(thumbnail);
                }
            } else {
                mTestResult.setTextColor(Color.parseColor("#FF5252"));
                mTestSubtext.setText("Check tunnel host/SNI or network connection and try again.");
            }
        });
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQ_SAF_PICK_TXT) {
            Uri uri = data.getData();
            int added = mEngine.importFromUri(uri);
            refreshConfigHeader();
            MessageHelpers.showMessage(mContext, "Imported " + added + " tunnel config(s) from .txt!");
        } else if (requestCode == REQ_MATERIAL_PICK_TXT) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            if (filePath != null) {
                int added = mEngine.importFromFile(new File(filePath));
                refreshConfigHeader();
                MessageHelpers.showMessage(mContext, "Imported " + added + " tunnel config(s) from " + new File(filePath).getName());
            }
        }
    }

    @Override
    public void onLogUpdated(List<String> allLogs, String latestLine) {
        renderLogs(allLogs);
    }

    private void renderLogs(List<String> logs) {
        if (mLogsView == null) return;
        StringBuilder sb = new StringBuilder();
        for (String line : logs) {
            sb.append(line).append("\n");
        }
        mLogsView.setText(sb.toString());
        if (mLogsScroll != null) {
            mLogsScroll.post(() -> mLogsScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    @Override
    public void onStateChanged(boolean connected, TunnelConfig activeConfig, String statusText) {
        refreshConnectionUi(connected, activeConfig);
        refreshConfigHeader();
    }
}
