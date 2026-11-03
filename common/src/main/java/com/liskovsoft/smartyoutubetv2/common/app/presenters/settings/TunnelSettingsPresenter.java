package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.tunnel.TunnelHubDialog;

public class TunnelSettingsPresenter extends BasePresenter<Void> {
    private TunnelSettingsPresenter(Context context) {
        super(context);
    }

    public static TunnelSettingsPresenter instance(Context context) {
        return new TunnelSettingsPresenter(context);
    }

    public void show() {
        new TunnelHubDialog(getContext()).show();
    }
}
