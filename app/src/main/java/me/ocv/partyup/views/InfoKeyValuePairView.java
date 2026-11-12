package me.ocv.partyup.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import me.ocv.partyup.databinding.InfoKeyValuePairElementBinding;

public class InfoKeyValuePairView extends LinearLayout {
    public InfoKeyValuePairView(Context context) {
        super(context);
        init(context);
    }

    public InfoKeyValuePairView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public InfoKeyValuePairView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private InfoKeyValuePairElementBinding binding;

    private void init(Context context) {
        binding = InfoKeyValuePairElementBinding.inflate(LayoutInflater.from(context), this, true);
    }

    public void setInfo(String key, String value) {
        if ((key == null || key.isEmpty()) || (value == null || value.isEmpty())) {
            binding.infoElementKey.setText("");
            binding.infoElementValue.setText("");
            return;
        }

        binding.infoElementKey.setText(key);
        binding.infoElementValue.setText(value);
    }
}
