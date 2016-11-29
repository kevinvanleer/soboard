package kvl.android.kvl.soboard;

import android.content.ContentValues;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by kvl on 10/16/16.
 */
public class TicketParser extends AsyncTask {

    private Uri imageUri;
    private String departureTime;
    private String flightNumber;
    private String airline;
    private String recognizedText;
    private Bitmap imageBitmap;
    private String imageName;
    private static final String LOG_TAG = "TicketParser";
    private static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/soboard/";
    private static final String lang = "eng";
    ImageListAdapter listAdapter;
    long recordId;
    boolean imageNotFound = true;

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getAirline() {
        return airline;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public Bitmap getImageBitmap() throws FileNotFoundException {
        if(imageBitmap == null) {
            try {
                imageBitmap = BitmapFactory.decodeStream(App.getContext().getContentResolver().openInputStream(imageUri));
            } catch (FileNotFoundException e) {
                imageNotFound = true;
                Log.e(LOG_TAG, imageUri.toString() + " not found");
                throw e;
            }
        }
        return imageBitmap;
    }

    public String getImageName() {
        if(imageName == null) {
            Cursor imageCursor = App.getContext().getContentResolver().query(imageUri, null, null, null, null);
            int nameIndex = imageCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            imageCursor.moveToFirst();
            imageName = imageCursor.getString(nameIndex);
        }
        return imageName;
    }

    public TicketParser(Uri imageUri, ImageListAdapter listAdapter, long recordId) throws FileNotFoundException {
        this.imageUri = imageUri;
        this.listAdapter = listAdapter;
        this.recordId = recordId;

        getImageBitmap();
    }


    public TicketParser(Uri imageUri, String airline, String flightNumber, String departureTime) throws FileNotFoundException {
        this.imageUri = imageUri;
        this.airline = airline;
        this.flightNumber = flightNumber;
        this.departureTime = departureTime;

        getImageBitmap();
    }

    @Override
    protected Object doInBackground(Object[] params) {
        initializeOcr();
        performOcr();
        return null;
    }

    @Override
    protected void onPostExecute(Object result) {
        Log.d(LOG_TAG, "Should update list");
        listAdapter.notifyDataSetChanged();
    }

    private void initializeOcr() {
        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/"};

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(LOG_TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(LOG_TAG, "Created directory " + path + " on sdcard");
                }
            }
        }

        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {
                AssetManager assetManager = App.getContext().getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(LOG_TAG, "Copied " + lang + ".traineddata");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Was unable to copy " + lang + ".traineddata " + e.toString());
            }
        }
    }

    private String findAirline(String[] lines) {
        String airline = null;
        for(String line : lines) {
            if (line.contains("Southwest")) {
                airline = "Southwest";
            } else if (line.contains("American")) {
                airline = "American";
            }
        }
        return airline;
    }

    private String findSouthwestFlightNumber(String line) {
        String flightNumber = null;
        String[] words = line.split(" ");
        if (words.length == 2) {
            flightNumber = words[1];
        }

        return flightNumber;
    }

    private String findSouthwestDepartureTime(String line) {
        String departureTime = null;
        String[] words = line.split(" ");
        if(words.length > 1) {
            departureTime = words[1] + " " + words[2];
        }

        return departureTime;
    }

    private void findSouthwestInfo(String[] lines) {
        for(String line : lines) {
            if(line.contains("Flight")) {
                flightNumber = findSouthwestFlightNumber(line);
            } else if (line.contains("Depart")) {
                departureTime = findSouthwestDepartureTime(line);
            }
        }
    }

    private String findAmericanFlightNumber(String keyLine, String valueLine) {
        String flightNumber = null;
        String[] words = valueLine.split(" ");

        if(words.length >= 2) {
            flightNumber = words[1];
            if(flightNumber.contains("AA")) {
                flightNumber = flightNumber.substring(2);
            }
        }

        return flightNumber;
    }

    private String findAmericanDepartureTime(String line) {
        String departureTime = null;
        String[] words = line.split(" ");
        if(words.length > 1) {
            departureTime = words[1];
        }

        return departureTime;
    }
    private void findAmericanInfo(String[] lines) {
        int lineIndex = 0;
        for(String line : lines) {
            if(line.contains("Flight")) {
                flightNumber = findAmericanFlightNumber(line, lines[lineIndex + 1]);
            } else if (line.contains("Depart")) {
                departureTime = findAmericanDepartureTime(line);
            }
            ++lineIndex;
        }
    }

    private void findFlightInfo(String recognizedText) {
        String[] lines = recognizedText.split("\\r?\\n");
        airline = findAirline(lines);
        if(airline != null) {
            if (airline.equals("Southwest")) {
                findSouthwestInfo(lines);
            } else if (airline.equals("American")) {
                findAmericanInfo(lines);
            }
        }
    }

    private void performOcr() {
        try {
            getImageBitmap();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, imageUri.toString() + " not found, cannot perform OCR");
            return;
        }
        /*
        FOR NOW ASSUME THE IMAGE IS NOT ROTATED
        if(imageBitmap.getWidth() > imageBitmap.getHeight()) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            imageBitmap = Bitmap.createScaledBitmap(imageBitmap, imageBitmap.getWidth(), imageBitmap.getHeight(), true);
            imageBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), matrix, true);
        }
        */

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(imageBitmap);

        recognizedText = baseApi.getUTF8Text();
        Log.v(LOG_TAG, recognizedText);

        findFlightInfo(recognizedText);

        ContentValues values = new ContentValues();
        values.put(DatabaseSchema.TicketInfo.COLUMN_NAME_AIRLINE, airline);
        values.put(DatabaseSchema.TicketInfo.COLUMN_NAME_FLIGHT_NUMBER, flightNumber);
        values.put(DatabaseSchema.TicketInfo.COLUMN_NAME_DEPARTURE_TIME, departureTime);
        String query = DatabaseSchema.TicketInfo._ID + " = " + recordId;

        SQLiteDatabase ticketDb = new TicketInfoHelper(App.getContext()).getWritableDatabase();
        ticketDb.update(DatabaseSchema.TicketInfo.TABLE_NAME, values, query, null);
    }
}
