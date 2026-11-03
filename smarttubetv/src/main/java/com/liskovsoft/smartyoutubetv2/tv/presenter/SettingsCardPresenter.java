package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultTextColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultTextColor = ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedTextColor = Color.parseColor("#00F2FE");

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);
        container.setBackgroundResource(R.drawable.aurora_card_bg);

        if (Helpers.isTouchSupported(context)) {
            container.setFocusableInTouchMode(false);
        }

        if (VERSION.SDK_INT >= 21) {
            final int radiusPx = context.getResources().getDimensionPixelSize(R.dimen.lb_rounded_rect_corner_radius);
            container.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
            container.setClipToOutline(true);
        }

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(Color.TRANSPARENT);
        textView.setTextColor(mDefaultTextColor);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> applyCardHighlight(context, v, textView, hasFocus));
        container.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                applyCardHighlight(context, v, textView, true);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT && !v.hasFocus()) {
                applyCardHighlight(context, v, textView, false);
            }
            return false;
        });

        return new ViewHolder(container);
    }

    private void applyCardHighlight(Context context, View container, TextView textView, boolean active) {
        container.setBackgroundResource(active ? R.drawable.aurora_card_focused_glow : R.drawable.aurora_card_bg);
        textView.setTextColor(active ? mSelectedTextColor : mDefaultTextColor);
        float scale = active ? 1.07f : 1.0f;
        container.animate().scaleX(scale).scaleY(scale).setDuration(180).start();
        if (VERSION.SDK_INT >= 21) {
            container.setElevation(active ? 18f : 4f);
        }
        if (active) {
            ViewUtil.enableMarquee(textView);
        } else {
            ViewUtil.disableMarquee(textView);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);
        textView.setText(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }
}
