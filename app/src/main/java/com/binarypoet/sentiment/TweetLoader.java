package com.binarypoet.sentiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.content.AsyncTaskLoader;

import twitter4j.Query;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

public class TweetLoader extends AsyncTaskLoader<List<Status>> {
    private final Twitter twitter;
    private final String consumerKey;
    private final String consumerSecret;

    private static final String PREF_NAME = "twitter_pref";
    private static final String PREF_KEY_OAUTH_TOKEN = "oauth_token";
    private static final String PREF_KEY_OAUTH_SECRET = "oauth_token_secret";
    private static final String PREF_KEY_TWITTER_LOGIN = "is_twitter_loggedin";
    private static final String PREF_USER_NAME = "twitter_user_name";
    private final SharedPreferences mSharedPreferences;

    private String searchTerm = null;

    List<Status> previousTeets = new ArrayList<Status>();

    public TweetLoader(Context context, String searchTerm) {
        super(context);
        this.searchTerm = searchTerm;

        ConfigurationBuilder builder = new ConfigurationBuilder();
        consumerKey = context.getResources().getString(R.string.twitter_consumer_key);
        consumerSecret = context.getResources().getString(R.string.twitter_consumer_secret);
        builder.setOAuthConsumerKey(consumerKey);
        builder.setOAuthConsumerSecret(consumerSecret);

        // Access Token
        mSharedPreferences = context.getSharedPreferences(PREF_NAME, 0);
        String access_token = mSharedPreferences.getString(PREF_KEY_OAUTH_TOKEN, "");
        // Access Token Secret
        String access_token_secret = mSharedPreferences.getString(PREF_KEY_OAUTH_SECRET, "");

        AccessToken accessToken = new AccessToken(access_token, access_token_secret);
        twitter = new TwitterFactory(builder.build()).getInstance(accessToken);

//        ConfigurationBuilder cb = new ConfigurationBuilder().setOAuthConsumerKey("ZKArtNdDFwUB9QChTewAkIQPx")
//                .setOAuthConsumerSecret("ixYyAwssTXc5bsWzdhH5S1sgXHknYhs4twHkrtjSSN5CIJX9v5")
//                .setOAuthAccessToken("14919086-gSQaF3wxo8d1TBuaC56NyofzHNf9E0SF2JZjdRKY9")
//                .setOAuthAccessTokenSecret("2tvftbKkV6cZ8xy5jNfQGpauH5WcRcG4tSClXsM8yvzHZ");
//        TwitterFactory tf = new TwitterFactory(cb.build());
//        twitter = tf.getInstance();
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    @Override
    public List<Status> loadInBackground() {
        List<Status> list = new ArrayList<Status>();
        try {
            if (searchTerm == null) {
                ResponseList<Status> rl = twitter.getHomeTimeline();
                Iterator<Status> iter = rl.iterator();
                while (iter.hasNext()) {
                    twitter4j.Status s = iter.next();
                    list.add(s);
                }
            } else {
                Query query = new Query(searchTerm);
                query.setLang("en");
                list.addAll(twitter.search(query).getTweets());
            }

            Collections.sort(list, new Comparator<Status>() {
                @Override
                public int compare(Status status, Status t1) {
                    return status.getCreatedAt().compareTo(t1.getCreatedAt());
                }
            });
            previousTeets.clear();
            previousTeets.addAll(list);
        } catch (TwitterException e) {
            list.addAll(previousTeets);
        }
        return list;
    }
} 