package kvl.android.kvl.soboard;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileNotFoundException;

public class BoardingPassActivity extends AppCompatActivity {
    private static final String LOG_TAG = "BoardingPassActivity";
    private final int ACTIVE_TICKET_NOTIFICATION = 1; //this.getResources().getInteger(R.integer.active_ticket_notification);
    Draper don;
    NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;

    @Override
    public void onBackPressed() {
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if(! myKM.inKeyguardRestrictedInputMode()) {
            if (don.interstitialReady()) {
                Log.v(LOG_TAG, "Displaying ad.");
                don.showInterstitialAd();
            }

            notificationManager.cancel(ACTIVE_TICKET_NOTIFICATION);

            super.onBackPressed();
        } else {
            Log.v(LOG_TAG, "Screen is locked, suppressing back button.");
            Toast toast = Toast.makeText(this, "Press home to dismiss ticket", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    Bitmap boardingPass;
    ImageView imageView;
    Activity context = this;

    void adjustBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        getWindow().setAttributes(lp);
    }

    void hideStatusBar() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideStatusBar();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        don = new Draper(context, true);
        don.requestNewInterstitial();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        adjustBrightness();

        setContentView(R.layout.activity_boarding_pass);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent input = getIntent();
        Uri imageUri = input.getParcelableExtra(WelcomeActivity.BOARDING_PASS_URI_KEY);
        Log.v(LOG_TAG, imageUri.toString());
        showNotification(input);

        imageView = (ImageView) findViewById(R.id.boardingPassImageView);

        try {
            boardingPass = BitmapFactory.decodeStream(this.getContentResolver().openInputStream(imageUri));
            if(boardingPass.getWidth() > boardingPass.getHeight()) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                boardingPass = Bitmap.createScaledBitmap(boardingPass,boardingPass.getWidth(),boardingPass.getHeight(),true);
                boardingPass = Bitmap.createBitmap(boardingPass, 0, 0, boardingPass.getWidth(), boardingPass.getHeight(), matrix, true);
            }
            imageView.setImageBitmap(boardingPass);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Image not found.");
            //setResult(Activity.RESULT_CANCELED);
            //finish();
        }

        imageView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void showNotification(Intent origIntent) {
        notificationBuilder = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.soboard_launcher)
                        .setContentTitle("You have an active ticket")
                        .setContentText(String.format("Tap to view %s", origIntent.getStringExtra(WelcomeActivity.BOARDING_PASS_NAME_KEY)))
                        .setPriority(Notification.PRIORITY_MAX).setOngoing(true);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(BoardingPassActivity.class);
        stackBuilder.addNextIntent(origIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder.setContentIntent(resultPendingIntent);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(ACTIVE_TICKET_NOTIFICATION, notificationBuilder.build());
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        hide();
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }
}
