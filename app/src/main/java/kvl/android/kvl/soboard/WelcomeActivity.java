package kvl.android.kvl.soboard;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class WelcomeActivity extends AppCompatActivity {

    private static final String LOG_TAG = "WelcomeActivity";

    private static final int PICK_IMAGE = 1;
    private static final int ADD_NEW_TICKET = 2;
    private static final int REBUILD_TICKET_LIST = 3;

    ListView boardingPassListView;
    static final String BOARDING_PASS_EXTRA = "kvl.android.kvl.soboard.boarding_pass";
    static final String SAVED_IMAGE_LIST = "kvl.android.kvl.soboard.savedImages";

    ImageListAdapter imageAdapter;
    final Activity context = this;

    SQLiteDatabase ticketDb;

    InterstitialAd interstitialAd;
    AdView adBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.ticketDb = new TicketInfoHelper(App.getContext()).getReadableDatabase();
        setContentView(R.layout.activity_welcome);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        MobileAds.initialize(context, getResources().getString(R.string.ad_mob_app_id));

        adBanner = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("A11960FBF8D4DAB9AFC3DE56A7D7C0D8")
                .build();
        adBanner.loadAd(adRequest);

        interstitialAd = new InterstitialAd(context);
        interstitialAd.setAdUnitId(getResources().getString(R.string.banner_ad_unit_id));
        requestNewInterstitial();

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (interstitialAd.isLoaded()) {
                    Log.v(LOG_TAG, "Displaying ad.");
                    interstitialAd.show();
                } else {
                    addNewTicket();
                }

                interstitialAd.setAdListener(new AdListener() {
                    @Override
                    public void onAdClosed() {
                        super.onAdClosed();
                        requestNewInterstitial();
                        addNewTicket();
                    }
                });

            }
        });

        imageAdapter = new ImageListAdapter(context, R.layout.image_list_item);
        boardingPassListView = (ListView) findViewById(R.id.boardingPassListView);
        boardingPassListView.setAdapter(imageAdapter);

        initializeImageListClickListener();
        initializeImageListLongClickListener();
        initializeImageListTouchListener();

        if(!(savedInstanceState == null || savedInstanceState.isEmpty())) {
            rebuildFromBundle(savedInstanceState);
        } else {
            rebuildFromDatabase();
        }
    }

    private void addNewTicket() {
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ADD_NEW_TICKET);
            Log.d(LOG_TAG, "requesting permissions to write external storage");
        } else {
            Log.d(LOG_TAG, "permission already granted");
            getImage();
        }
    }

    private void requestNewInterstitial() {
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("A11960FBF8D4DAB9AFC3DE56A7D7C0D8")
                .build();

        interstitialAd.loadAd(adRequest);
    }

    private void rebuildFromDatabase() {
        Cursor tickets = ticketDb.query(DatabaseSchema.TicketInfo.TABLE_NAME, null, null, null, null, null, null, null);

        if (tickets.getCount() > 0 && PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REBUILD_TICKET_LIST);
            Log.d(LOG_TAG, "requesting permissions to write external storage");
        } else {
            Log.d(LOG_TAG, "permission already granted");
            buildTicketList(tickets);
        }

    }

    private void buildTicketList(Cursor tickets) {
        tickets.moveToFirst();
        while(!tickets.isAfterLast()) {
            try {
                ImageListItem item = new ImageListItem(tickets);
                imageAdapter.add(item);

            } catch (FileNotFoundException e) {
                Log.w(LOG_TAG, "Image no longer exists at the saved URI. The ticket will not be displayed. Ticket will be removed from database.");
                ticketDb.delete(DatabaseSchema.TicketInfo.TABLE_NAME, DatabaseSchema.TicketInfo._ID + " = " + tickets.getLong(tickets.getColumnIndex(DatabaseSchema.TicketInfo._ID)), null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "A ticket in the database could not be added to list.", e);
            }
            tickets.moveToNext();
        }
        tickets.close();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelableArrayList(SAVED_IMAGE_LIST, imageAdapter.getArrayList());
    }

    private void rebuildFromBundle(Bundle state) {
        ArrayList<ImageListItem> savedImages = state.getParcelableArrayList(SAVED_IMAGE_LIST);
        if(savedImages != null) {
            for (ImageListItem image : savedImages) {
                imageAdapter.add(image);
            }
        }
    }

    float startX;
    float startY;
    int startLeft;
    View deleteView;
    View moveView;
    boolean shouldDelete = false;
    boolean suppressLongPress = true;
    boolean scrolling = false;
    boolean sliding = false;
    private void initializeImageListTouchListener() {
        boardingPassListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean returning = false;
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        returning = handleActionDown(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        returning = handleActionMove(event);
                        break;
                    case MotionEvent.ACTION_UP:
                        returning = handleActionUp(event);
                        break;
                    default:
                        break;
                }
                if(returning) {
                    Log.v(LOG_TAG, "returning true");
                } else {
                    Log.v(LOG_TAG, "returning false");
                }
                return returning;
            }

            private boolean handleActionUp(MotionEvent event) {
                boolean returning = true;
                Log.d(LOG_TAG, "End touch sequence");
                longPressHandler.removeCallbacks(handleLongPress);

                if(scrolling) returning = false;

                if (!(sliding || scrolling)) {
                    Log.d(LOG_TAG, "This is a click");
                    deleteView.performClick();
                    returning = false;
                } else {
                    if (event.getEventTime() - event.getDownTime() < 200) {
                        if (event.getRawX() - startX > boardingPassListView.getWidth() * 0.25) {
                            shouldDelete = true;
                        }
                    }
                    if (shouldDelete) {
                        deleteItem(deleteView);
                    } else {
                        moveView.setAlpha(1f);
                        moveView.setLeft(startLeft);
                    }
                }

                sliding = false;
                scrolling = false;
                return returning;
            }

            private void deleteItem(final View deleteView) {
                final int deletePosition = boardingPassListView.getPositionForView(deleteView);
                Log.d(LOG_TAG, "Deleting item at " + deletePosition);
                moveView.animate()
                    .translationXBy(boardingPassListView.getWidth())
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            //deleteView.setVisibility(View.GONE);
                            //NOTE THIS WORKS IF I MOVE THE REMOVE CALL OUT OF THE ANIMATION
                            imageAdapter.getItem(deletePosition).removeFromDb();
                            imageAdapter.remove(imageAdapter.getItem(deletePosition));
                        }
                    });
            }

            private boolean handleActionMove(MotionEvent event) {
                if(scrolling) {
                    return false;
                }

                if (sliding) {
                    handleSwipe(event);
                } else {
                    if(Math.abs(event.getRawX() - startX)  < 5 &&
                        Math.abs(event.getRawY() - startY)  < 5) {
                        Log.v(LOG_TAG, "Haven't moved");
                    } else {
                        Log.v(LOG_TAG, "Cancelling long press");
                        longPressHandler.removeCallbacks(handleLongPress);

                        if (Math.abs(event.getRawY() - startY) > 10) {
                            Log.d(LOG_TAG, "User is scrolling the list");
                            scrolling = true;
                        } else {
                            handleSwipe(event);
                        }
                    }
                }

                return sliding;
            }

            private void handleSwipe(MotionEvent event) {
                sliding = true;
                if (imageAdapter.isEditing()) {
                    moveView.setLeft(startLeft);
                } else {
                    if (event.getRawX() < startX) {
                        Log.v(LOG_TAG, "Resetting startX");
                        startX = event.getRawX();
                    } else {
                        if (event.getRawX() - startX > boardingPassListView.getWidth() * 0.5) {
                            if (!shouldDelete) {
                                Log.d(LOG_TAG, "Will delete item");
                                shouldDelete = true;
                            }
                        } else if (shouldDelete) {
                            Log.d(LOG_TAG, "Will not delete item");
                            shouldDelete = false;
                        } else {
                            Log.d(LOG_TAG, "No change.");
                        }

                        moveView.setLeft((int) (startLeft + event.getRawX() - startX));
                        moveView.setAlpha(1.f - ((event.getRawX() - startX) / moveView.getWidth()));
                    }
                }
            }

            final Handler longPressHandler = new Handler();
            Runnable handleLongPress = new Runnable() {
                public void run() {
                    Log.i(LOG_TAG, "Long press!");
                    suppressLongPress = false;
                    deleteView.performLongClick();
                    suppressLongPress = true;
                }
            };

            private boolean handleActionDown(MotionEvent event) {
                startX = event.getRawX();
                startY = event.getRawY();
                shouldDelete = false;
                Rect rect = new Rect();
                int childCount = boardingPassListView.getChildCount();
                int[] listViewCoords = new int[2];
                boardingPassListView.getLocationOnScreen(listViewCoords);
                int x = (int) event.getRawX() - listViewCoords[0];
                int y = (int) event.getRawY() - listViewCoords[1];
                View child;
                for (int i = 0; i < childCount; i++) {
                    child = boardingPassListView.getChildAt(i);
                    child.getHitRect(rect);
                    if (rect.contains(x, y)) {
                        deleteView = child;
                        break;
                    }
                }
                moveView = deleteView.findViewById(R.id.layout_imageListItem);
                startLeft = deleteView.getLeft();
                longPressHandler.postDelayed(handleLongPress, android.view.ViewConfiguration.getLongPressTimeout());
                return false;
            }
        });
    }

    private void initializeImageListClickListener() {
        boardingPassListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (imageAdapter.isEditing()) {
                    return;
                }
                imageAdapter.stopEditing(boardingPassListView);
                Intent displayImage = new Intent(context, BoardingPassActivity.class);
                displayImage.putExtra(BOARDING_PASS_EXTRA, imageAdapter.getItem(position).getImageUri());
                startActivity(displayImage);
            }
        });
    }

    private void initializeImageListLongClickListener() {
        boardingPassListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (imageAdapter.isEditing() || suppressLongPress) {
                    return false;
                }
                return imageAdapter.makeEditable(view, position);
            }
        });
    }

    void getImage() {
        Log.d(LOG_TAG, "The user will now select an image to view");
        Intent intent = new Intent();
        intent.setType("image/*");
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_IMAGE);
        } else {
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select image"), PICK_IMAGE);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {

            case ADD_NEW_TICKET:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(LOG_TAG, "WRITE_EXTERNAL_STORAGE permission granted");
                    getImage();
                } else {
                    Log.d(LOG_TAG, "WRITE_EXTERNAL_STORAGE permission denied");
                }
                break;
            case REBUILD_TICKET_LIST:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(LOG_TAG, "WRITE_EXTERNAL_STORAGE permission granted");
                    buildTicketList(ticketDb.query(DatabaseSchema.TicketInfo.TABLE_NAME, null, null, null, null, null, null, null));
                } else {
                    Log.d(LOG_TAG, "WRITE_EXTERNAL_STORAGE permission denied");
                }
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        switch (requestCode) {
            case PICK_IMAGE:
                if(data == null) {
                    Log.d(LOG_TAG, "activity finished with no image selected");
                    return;
                }
                try {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    ImageListItem newItem = new ImageListItem(data.getData(), imageAdapter);
                    imageAdapter.add(newItem);
                    Intent displayImage = new Intent(this, BoardingPassActivity.class);
                    displayImage.putExtra(BOARDING_PASS_EXTRA, newItem.getImageUri());
                    startActivity(displayImage);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG, "Chosen image does not exist, this can't happen");
                }
            default:
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_welcome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
