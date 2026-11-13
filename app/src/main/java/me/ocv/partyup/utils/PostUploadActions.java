package me.ocv.partyup.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.jetbrains.annotations.Contract;

import me.ocv.partyup.R;

public final class PostUploadActions {
    private static final String TAG = "PostUploadAction";
    private static final int QR_SIZE = 256;

    @NonNull
    private String getString(int resId) {
        return context.getString(resId);
    }

    public void copyLink(String shareUrl) {
        ClipData cd = ClipData.newPlainText(getString(R.string.clipboard_label), shareUrl);
        clipboardManager.setPrimaryClip(cd);

        toast(getString(R.string.copy_success));
        onDismiss.run();
    }

    public void shareLink(String shareUrl) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_extra_text));
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_extra_text) + "\n" + shareUrl);

        Intent chooser = Intent.createChooser(send, getString(R.string.share_link_title));
        context.getApplicationContext().startActivity(chooser);
        onDismiss.run();
    }

    public void showQRLink(String shareUrl) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                    shareUrl,
                    BarcodeFormat.QR_CODE,
                    QR_SIZE,
                    QR_SIZE
            );

            Bitmap shareQr = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.RGB_565);

            for (int x = 0; x < QR_SIZE; x++) {
                for (int y = 0; y < QR_SIZE; y++) {
                    shareQr.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ImageView shownImage = new ImageView(context);
            shownImage.setImageBitmap(shareQr);
            shownImage.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            shownImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            shownImage.setAdjustViewBounds(true);

            AlertDialog dialog = new AlertDialog.Builder(context).setView(shownImage)
                    .setCancelable(true)
                    .setOnDismissListener(d -> onDismiss.run())
                    .create();

            dialog.show();
        } catch (WriterException e) {
            toast(e.getLocalizedMessage());
            Log.e(TAG, "Unable to show QR", e);
            onDismiss.run();
        }
    }

    private void toast(String msg) {
        ToastUtils.show(context, msg);
    }

    private final Context context;
    private final ClipboardManager clipboardManager;
    private final Runnable onDismiss;

    private PostUploadActions(Context context, Runnable onDismiss) {
        this.context = context;
        this.onDismiss = onDismiss;
        this.clipboardManager = this.context.getSystemService(ClipboardManager.class);
    }

    @NonNull
    @Contract(value = "_, _ -> new", pure = true)
    public static PostUploadActions create(Context context, Runnable onDismiss) {
        return new PostUploadActions(context, onDismiss);
    }
}
