package com.binarypoet.sentiment;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<Status>>, View.OnClickListener {

    private TweetAdapter adapter;
    private String searchTerm = null;
    private int selectedPosition = -1;
    private String consumerKey;

    /* Shared preference keys */
    private static final String PREF_NAME = "twitter_pref";
    private static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
    private static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
    private static final String PREF_KEY_TWITTER_LOGIN = "is_twitter_loggedin";
    private static final String PREF_USER_NAME = "twitter_user_name";
    public static final int WEBVIEW_REQUEST_CODE = 100;

    private SharedPreferences mSharedPreferences;
    private String consumerSecret;
    private String callbackUrl;
    private String oAuthVerifier;
    private Twitter twitter;
    private RequestToken requestToken;
    private LinearLayout loginLayout;
    private LinearLayout listLayout;
    private TextView userName;
    private ListView lv;

    @Override
    public Loader<List<Status>> onCreateLoader(int id, Bundle args) {
        return new TweetLoader(MainActivity.this, searchTerm);
    }

    @Override
    public void onLoadFinished(Loader<List<Status>> loader, List<Status> data) {
        adapter.setTweets(data);
    }

    @Override
    public void onLoaderReset(Loader<List<Status>> loader) {
        adapter.setTweets(new ArrayList<Status>());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Timeline");
        initTwitterConfigs();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSharedPreferences = getSharedPreferences(PREF_NAME, 0);

        loginLayout = (LinearLayout) findViewById(R.id.login_layout);
        listLayout = (LinearLayout) findViewById(R.id.list_layout);
        userName = (TextView) findViewById(R.id.userName);
        /* register button click listeners */
        findViewById(R.id.btnLoginTwitter).setOnClickListener(this);


        boolean isLoggedIn = mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);

		/*  if already logged in, then hide login layout and show share layout */
        if (isLoggedIn) {
            loginLayout.setVisibility(View.GONE);
            listLayout.setVisibility(View.VISIBLE);
            lv = (ListView) findViewById(R.id.list_view);
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (selectedPosition == position) {
                        selectedPosition = -1;
                    } else {
                        selectedPosition = position;
                    }
                    adapter.setSelection(selectedPosition);
                }
            });

            adapter = new TweetAdapter(this, new ArrayList<Status>());
            lv.setAdapter(adapter);
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setOnClickListener(this);
            getSupportLoaderManager().initLoader(1, null, this).forceLoad();
        } else {
            loginLayout.setVisibility(View.VISIBLE);
            listLayout.setVisibility(View.GONE);

            Uri uri = getIntent().getData();

            if (uri != null && uri.toString().startsWith(callbackUrl)) {

                String verifier = uri.getQueryParameter(oAuthVerifier);

                try {

					/* Getting oAuth authentication token */
                    AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);

					/* Getting user id form access token */
                    long userID = accessToken.getUserId();
                    final User user = twitter.showUser(userID);
                    final String username = user.getName();

					/* save updated token */
                    saveTwitterInfo(accessToken);

                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Twitter getTwitter() {
        return twitter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final MenuItem myActionMenuItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) myActionMenuItem.getActionView();
        final LoaderManager.LoaderCallbacks<List<Status>> foo = this;
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchTerm = query.trim().toLowerCase();
                if (searchTerm.isEmpty()) {
                    searchTerm = null;
                    setTitle("Timeline");
                } else {
                    setTitle(": " + searchTerm);
                }
                adapter.setSelection(-1);
                getSupportLoaderManager().restartLoader(1, null, foo).forceLoad();
                searchView.setQuery("", false);
                searchView.setIconified(true);
                myActionMenuItem.collapseActionView();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                // UserFeedback.show( "SearchOnQueryTextChanged: " + s);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_add) {
            if (selectedPosition != -1) {
                Status status = adapter.getItem(selectedPosition);
                adapter.train(status, 1);
                Toast.makeText(MainActivity.this, "Upvoting status.",
                        Toast.LENGTH_LONG).show();
                adapter.setSelection(-1);
                selectedPosition = -1;
            }
        } else if (id == R.id.action_delete) {
            if (selectedPosition != -1) {
                Status status = adapter.getItem(selectedPosition);
                adapter.train(status, -1);
                Toast.makeText(MainActivity.this, "Downvoting status.",
                        Toast.LENGTH_LONG).show();
                adapter.setSelection(-1);
                selectedPosition = -1;
            }
        } else if (id == R.id.action_logout) {
            logoutFromTwitter();
        } else if (id == R.id.action_clear_search) {
            adapter.setSelection(-1);
            selectedPosition = -1;
            setTitle("Timeline");
            searchTerm = null;
            final LoaderManager.LoaderCallbacks<List<Status>> foo = this;
            getSupportLoaderManager().restartLoader(1, null, foo).forceLoad();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLoginTwitter:
                loginToTwitter();
                break;
            case R.id.fab:
                Snackbar.make(v, "Reloading list", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Action", null).show();
                adapter.setSelection(-1);
                getSupportLoaderManager().restartLoader(1, null, this).forceLoad();
                break;
        }
    }

    /**
     * Saving user information, after user is authenticated for the first time.
     * You don't need to show user to login, until user has a valid access toen
     */
    private void saveTwitterInfo(AccessToken accessToken) {

        long userID = accessToken.getUserId();

        User user;
        try {
            user = twitter.showUser(userID);

            String username = user.getName();

			/* Storing oAuth tokens to shared preferences */
            SharedPreferences.Editor e = mSharedPreferences.edit();
            e.putString(PREF_KEY_OAUTH_TOKEN, accessToken.getToken());
            e.putString(PREF_KEY_OAUTH_SECRET, accessToken.getTokenSecret());
            e.putBoolean(PREF_KEY_TWITTER_LOGIN, true);
            e.putString(PREF_USER_NAME, username);
            e.commit();

        } catch (TwitterException e1) {
            e1.printStackTrace();
        }
    }

    /* Reading twitter essential configuration parameters from strings.xml */
    private void initTwitterConfigs() {
        consumerKey = getString(R.string.twitter_consumer_key);
        consumerSecret = getString(R.string.twitter_consumer_secret);
        callbackUrl = getString(R.string.twitter_callback);
        oAuthVerifier = getString(R.string.twitter_oauth_verifier);
    }


    private void loginToTwitter() {
        boolean isLoggedIn = mSharedPreferences.getBoolean(PREF_KEY_TWITTER_LOGIN, false);

        if (!isLoggedIn) {
            final ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.setOAuthConsumerKey(consumerKey);
            builder.setOAuthConsumerSecret(consumerSecret);

            final Configuration configuration = builder.build();
            final TwitterFactory factory = new TwitterFactory(configuration);
            twitter = factory.getInstance();

            try {
                requestToken = twitter.getOAuthRequestToken(callbackUrl);

                /**
                 *  Loading twitter login page on webview for authorization
                 *  Once authorized, results are received at onActivityResult
                 *  */
                final Intent intent = new Intent(this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.EXTRA_URL, requestToken.getAuthenticationURL());
                startActivityForResult(intent, WEBVIEW_REQUEST_CODE);

            } catch (TwitterException e) {
                e.printStackTrace();
            }
        } else {

            loginLayout.setVisibility(View.GONE);
            listLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK) {
            String verifier = data.getExtras().getString(oAuthVerifier);
            try {
                AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);

                long userID = accessToken.getUserId();
                final User user = twitter.showUser(userID);
                String username = user.getName();

                saveTwitterInfo(accessToken);

                Intent intent = getIntent();
                finish();
                startActivity(intent);
            } catch (Exception e) {
                Log.e("Twitter Login Failed", e.getMessage());
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void logoutFromTwitter() {
        // Clear the shared preferences
        SharedPreferences.Editor e = mSharedPreferences.edit();
        e.remove(PREF_KEY_OAUTH_TOKEN);
        e.remove(PREF_KEY_OAUTH_SECRET);
        e.remove(PREF_KEY_TWITTER_LOGIN);
        e.commit();

        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }
}
