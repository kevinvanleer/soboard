package kvl.android.kvl.soboard;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.FileNotFoundException;

/**
 * Created by kvl on 8/19/16.
 */
public final class ImageListItem implements Parcelable {
    private Uri imageUri;

    private String userDefinedName = null;

    private static final String LOG_TAG = "ImageListItem";

    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/soboard/";
    public static final String lang = "eng";

    private TicketParser ticketInfo;

    private SQLiteDatabase ticketDb;

    private long recordId;

    public ImageListItem(Uri image, ImageListAdapter listAdapter) throws FileNotFoundException {
        this.imageUri = image;

        ticketDb = new TicketInfoHelper(App.getContext()).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseSchema.TicketInfo.COLUMN_NAME_IMAGE_URI, imageUri.toString());
        recordId = ticketDb.insert(DatabaseSchema.TicketInfo.TABLE_NAME, null, values);

        ticketInfo = new TicketParser(this.imageUri, listAdapter, recordId);
        ticketInfo.execute();
    }

    public ImageListItem(Parcel in) {
        readFromParcel(in);
        ticketDb = new TicketInfoHelper(App.getContext()).getWritableDatabase();
    }

    public void removeFromDb() {
        ticketDb.delete(DatabaseSchema.TicketInfo.TABLE_NAME, DatabaseSchema.TicketInfo._ID + " = " + recordId, null);
    }

    public ImageListItem(Cursor items) throws FileNotFoundException {
        userDefinedName = items.getString(items.getColumnIndex(DatabaseSchema.TicketInfo.COLUMN_NAME_USER_DEFINED_NAME));
        recordId = items.getLong(items.getColumnIndex(DatabaseSchema.TicketInfo._ID));
        imageUri = Uri.parse(items.getString(items.getColumnIndex(DatabaseSchema.TicketInfo.COLUMN_NAME_IMAGE_URI)));

        String airline = items.getString(items.getColumnIndex(DatabaseSchema.TicketInfo.COLUMN_NAME_AIRLINE));
        String flightNumber = items.getString(items.getColumnIndex(DatabaseSchema.TicketInfo.COLUMN_NAME_FLIGHT_NUMBER));
        String departureTime = items.getString(items.getColumnIndex(DatabaseSchema.TicketInfo.COLUMN_NAME_DEPARTURE_TIME));

        ticketInfo = new TicketParser(imageUri, airline, flightNumber, departureTime);
        ticketDb = new TicketInfoHelper(App.getContext()).getWritableDatabase();
    }

    public long getRecordId() { return recordId; }

    public void setName(String name) {
        if(!name.equals(userDefinedName)) {
            if (name != null && !name.isEmpty() && !name.trim().equals(getName())) {
                userDefinedName = name.trim();
            } else {
                userDefinedName = null;
            }

            ContentValues values = new ContentValues();
            values.put(DatabaseSchema.TicketInfo.COLUMN_NAME_USER_DEFINED_NAME, userDefinedName);
            String query = DatabaseSchema.TicketInfo._ID + " = " + recordId;
            ticketDb.update(DatabaseSchema.TicketInfo.TABLE_NAME, values, query, null);
        }
    }

    public String getName() {
        String name = "";
        if (userDefinedName != null) {
            name = userDefinedName;
        }
        else {
            if (ticketInfo != null && ticketInfo.getFlightNumber() != null) {
                name += ticketInfo.getAirline() + " ";
            }
            if (ticketInfo != null && ticketInfo.getFlightNumber() != null) {
                name += "Flight " + ticketInfo.getFlightNumber() + " ";
            }
            if (name.isEmpty()) {
                name = ticketInfo.getImageName();
            }
            name = name.trim();
        }
        return name;
    }

    public Bitmap getImageBitmap() throws FileNotFoundException {
        return ticketInfo.getImageBitmap();
    }

    public Bitmap getScaledImageBitmap(int viewWidth, int viewHeight) throws FileNotFoundException {
        Bitmap original = getImageBitmap();
        int width = viewWidth;
        int height = viewHeight;
        float scale;
        if(original.getHeight() > original.getWidth()) {
            scale = (float) viewHeight / original.getHeight();
            width = (int) (scale * original.getWidth());
        } else {
            scale = (float) viewWidth / original.getWidth();
            height = (int) (scale * original.getHeight());
        }

        return Bitmap.createScaledBitmap(original, width, height, true);
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public Uri getImageUri() {
        return imageUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.imageUri, flags);
    }

    private void readFromParcel(Parcel in) {
        imageUri = in.readParcelable(null);
    }

    public static final Parcelable.Creator<ImageListItem> CREATOR = new Parcelable.Creator<ImageListItem>() {
        public ImageListItem createFromParcel(Parcel in) {
            return new ImageListItem(in);
        }

        public ImageListItem[] newArray(int size) {
            return new ImageListItem[size];
        }
    };
}
