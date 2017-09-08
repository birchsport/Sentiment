package com.binarypoet.sentiment;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.util.LruCache;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.binarypoet.sentiment.bayes.BayesClassifier;
import com.binarypoet.sentiment.bayes.BayesWordLists;
import com.binarypoet.sentiment.bayes.Classification;
import com.binarypoet.sentiment.bayes.Classifier;
import com.binarypoet.sentiment.db.StatusReaderContract;
import com.binarypoet.sentiment.db.StatusReaderDbHelper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import twitter4j.Status;

/**
 * Created by jbirchfield on 10/29/16.
 */

public class TweetAdapter extends BaseAdapter {

    private LruCache<String, Bitmap> mMemoryCache;


    private LayoutInflater inflater;
    private Classifier<String, String> bayes;
    private StatusReaderDbHelper dbHelper;

    private List<Status> tweets = new ArrayList<Status>();
    private int selectedPosition = -1;
    private View lastSelectedRow;

    public TweetAdapter(Context context, List<Status> tweets) {
        this.tweets = tweets;
        inflater = LayoutInflater.from(context);
        dbHelper = new StatusReaderDbHelper(context);

        bayes = new BayesClassifier<String, String>();

        bayes.learn("-1", Arrays.asList(BayesWordLists.NEGATIVE_WORDS));
        bayes.learn("+1", Arrays.asList(BayesWordLists.POSITIVE_WORDS));

        SQLiteDatabase database = dbHelper.getReadableDatabase();

        String[] projection = {
                StatusReaderContract.StatusEntry._ID,
                StatusReaderContract.StatusEntry.COLUMN_NAME_ID,
                StatusReaderContract.StatusEntry.COLUMN_NAME_SCORE,
                StatusReaderContract.StatusEntry.COLUMN_NAME_TEXT
        };

        String selection = StatusReaderContract.StatusEntry.COLUMN_NAME_ID + " = ?";
        String[] selectionArgs = {"My Title"};

// How you want the results sorted in the resulting Cursor
        String sortOrder =
                StatusReaderContract.StatusEntry.COLUMN_NAME_ID + " DESC";

        Cursor c = database.query(
                StatusReaderContract.StatusEntry.TABLE_NAME,                     // The table to query
                projection,                               // The columns to return
                null,                                // The columns for the WHERE clause
                null,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );
        while (c.moveToNext()) {
            String text = c.getString(c.getColumnIndex(StatusReaderContract.StatusEntry.COLUMN_NAME_TEXT));
            text = text
                    .replaceAll("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", "")
                    .replaceAll("#[A-Za-z]+", "")
                    .replaceAll("@[A-Za-z]+", "")
                    .replaceAll("RT", "")
                    .replaceAll(":", "");
            int score = c.getInt(c.getColumnIndex(StatusReaderContract.StatusEntry.COLUMN_NAME_SCORE));
            if (score < 0) {
                bayes.learn("-1", Arrays.asList(text.split("\\s+")));
            } else {
                bayes.learn("+1", Arrays.asList(text.split("\\s+")));
            }
        }

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

    }

    public void train(Status status, int score) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(StatusReaderContract.StatusEntry.COLUMN_NAME_ID, status.getId());
        values.put(StatusReaderContract.StatusEntry.COLUMN_NAME_SCORE, score);
        values.put(StatusReaderContract.StatusEntry.COLUMN_NAME_TEXT, status.getText());

        db.insert(StatusReaderContract.StatusEntry.TABLE_NAME, null, values);

        String text = status.getText()
                .replaceAll("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", "")
                .replaceAll("#[A-Za-z]+", "")
                .replaceAll("@[A-Za-z]+", "")
                .replaceAll("RT", "")
                .replaceAll(":", "");
        if (score < 0) {
            bayes.learn("-1", Arrays.asList(text.split("\\s+")));
        } else {
            bayes.learn("+1", Arrays.asList(text.split("\\s+")));
        }
    }

    @Override
    public View getView(int position, View view, final ViewGroup parent) {
        final Status tweet = (Status) getItem(position);
        if (view == null) {
            view = inflater.inflate(R.layout.list_tweets, null);
        }
        TextView tweetView = (TextView) view.findViewById(R.id.tweet_text);
        tweetView.setMovementMethod(LinkMovementMethod.getInstance());
        tweetView.setText(tweet.getText());

        TextView userView = (TextView) view.findViewById(R.id.tweet_user);
        userView.setText(tweet.getUser().getScreenName());


        TextView catView = (TextView) view.findViewById(R.id.tweet_cat);
        Classification<String, String> classification = getCategory(tweet);
        float probability = classification.getProbability();
        String cat = classification.getCategory();
        DecimalFormat df = new DecimalFormat("#.00000");
        if (probability < 0.0001) {
            cat = "0";
        }
        catView.setText(cat);
        switch (cat) {
            case "+1": {
                catView.setTextColor(Color.GREEN);
                break;
            }
            case "-1": {
                catView.setTextColor(Color.RED);
                break;
            }
            default: {
                catView.setTextColor(Color.BLACK);
            }
        }

        Bitmap bm = mMemoryCache.get(tweet.getUser().getProfileImageURL());
        if (bm == null) {
            bm = downloadBitmap(tweet.getUser().getProfileImageURL());
            mMemoryCache.put(tweet.getUser().getProfileImageURL(), bm);
        }
        ImageView imgView = (ImageView) view.findViewById(R.id.tweet_image);
        imgView.setImageBitmap(bm);
        imgView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://twitter.com/" + tweet.getUser().getScreenName()));
                parent.getContext().startActivity(intent);
            }
        });
        if (position == selectedPosition && position != -1) {
            view.setBackgroundResource(R.color.pressed_color);
        } else {
            view.setBackgroundResource(R.color.default_color);

        }

        return view;
    }

    @Override
    public Status getItem(int position) {
        return tweets.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return tweets.size();
    }

    public void setTweets(List<Status> data) {
        tweets.clear();
        tweets.addAll(data);
        notifyDataSetChanged();
    }

    private Bitmap downloadBitmap(String url) {
        HttpURLConnection urlConnection = null;
        try {
            URL uri = new URL(url);
            urlConnection = (HttpURLConnection) uri.openConnection();
            int statusCode = urlConnection.getResponseCode();
            if (statusCode != 200) {
                return null;
            }

            InputStream inputStream = urlConnection.getInputStream();
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                return bitmap;
            }
        } catch (Exception e) {
            urlConnection.disconnect();
            Log.w("ImageDownloader", "Error downloading image from " + url);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return null;
    }

    public void setSelection(int selectedPosition) {
        this.selectedPosition = selectedPosition;
        notifyDataSetChanged();

    }

    private Classification<String, String> getCategory(Status status) {
        String toClassify = status.getText()
                .replaceAll("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]", "")
                .replaceAll("#[A-Za-z]+", "")
                .replaceAll("@[A-Za-z]+", "")
                .replaceAll("RT", "")
                .replaceAll(":", "");
        Classification<String, String> classification = bayes.classify(Arrays.asList(toClassify.split("\\s")));
        return classification;
    }


}
