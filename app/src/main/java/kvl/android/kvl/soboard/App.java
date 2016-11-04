package kvl.android.kvl.soboard;

import android.app.Application;
import android.content.Context;

/**
 * Created by kvl on 11/4/16.
 */
public class App extends Application {
    private static Context context;

    public void onCreate() {
        super.onCreate();
        App.context = getApplicationContext();
    }

    public static Context getContext() {
        return App.context;
    }
}
