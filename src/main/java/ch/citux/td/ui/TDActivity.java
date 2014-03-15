package ch.citux.td.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.holoeverywhere.app.Activity;

import ch.citux.td.R;
import ch.citux.td.config.TDConfig;
import ch.citux.td.data.model.Channel;
import ch.citux.td.data.worker.TDTaskManager;
import ch.citux.td.ui.fragments.ChannelFragment;
import ch.citux.td.ui.fragments.FavoritesFragment;
import ch.citux.td.ui.fragments.SettingsFragment;
import ch.citux.td.util.Log;

public class TDActivity extends Activity {

    private FavoritesFragment favoritesFragment;
    private ChannelFragment channelFragment;

    private MenuItem settingsItem;
    private boolean isLoading;
    private MenuItem refreshItem;

    //Casting
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private MediaRouter.Callback mediaRouterCallback;
    private CastDevice castDevice;
    private Cast.Listener castClientListener;
    private GoogleApiClient apiClient;
    private GoogleApiClient.ConnectionCallbacks connectionCallbacks;
    private GoogleApiClient.OnConnectionFailedListener connectionFailedListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        favoritesFragment = new FavoritesFragment();
        channelFragment = new ChannelFragment();
        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(TDConfig.CAST_APPLICATION_ID))
                .build();
        mediaRouterCallback = new TDMediaRouterCallback();
        castClientListener = new TDCastListener();
        connectionCallbacks = new ConnectionCallbacks();
        connectionFailedListener = new ConnectionFailedListener();

        if (getSupportFragmentManager().findFragmentById(R.id.content) == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.content, favoritesFragment);
            if (findViewById(R.id.detail) != null) { //Tablet
                transaction.add(R.id.detail, channelFragment);
            }
            transaction.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    public void onPause() {
        if (isFinishing()) {
            mediaRouter.removeCallback(mediaRouterCallback);
        }
        TDTaskManager.cancelAllTasks();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        refreshItem = menu.findItem(R.id.menu_refresh);
        settingsItem = menu.findItem(R.id.menu_settings);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.menu_media_route);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                if (!isLoading) {
                    refreshData();
                }
                return true;
            case R.id.menu_settings:
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction
                        .replace(R.id.content, new SettingsFragment())
                        .addToBackStack(SettingsFragment.class.getSimpleName())
                        .commit();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void showChannel(Channel channel) {
        if (channelFragment.isAdded()) {
            channelFragment.updateChannel(channel);
        } else {
            Bundle arguments = new Bundle();
            arguments.putSerializable(ChannelFragment.CHANNEL, channel);
            channelFragment.setArguments(arguments);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.content, channelFragment).addToBackStack(ChannelFragment.class.getSimpleName()).commit();
        }

    }

    public void startLoading() {
        isLoading = true;
        if (refreshItem != null) {
            MenuItemCompat.setActionView(refreshItem, R.layout.action_refresh);
        }
    }

    public void stopLoading() {
        isLoading = false;
        if (refreshItem != null) {
            MenuItemCompat.setActionView(refreshItem, null);
        }
    }

    public void showOptions() {
        if (refreshItem != null) {
            refreshItem.setVisible(true);
        }
        if (settingsItem != null) {
            settingsItem.setVisible(true);
        }
    }

    public void hideOptions() {
        if (refreshItem != null) {
            refreshItem.setVisible(false);
        }
        if (settingsItem != null) {
            settingsItem.setVisible(false);
        }
    }

    protected void refreshData() {
        if (favoritesFragment != null && favoritesFragment.isAdded()) {
            favoritesFragment.refreshData();
        }
        if (channelFragment != null && channelFragment.isAdded()) {
            channelFragment.refreshData();
        }
    }

    private class TDMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            castDevice = CastDevice.getFromBundle(info.getExtras());
            String routeId = info.getId();

            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(castDevice, castClientListener);

            apiClient = new GoogleApiClient.Builder(TDActivity.this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(connectionCallbacks)
                    .addOnConnectionFailedListener(connectionFailedListener)
                    .build();
            apiClient.connect();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
//            teardown();
            castDevice = null;
        }
    }

    private class TDCastListener extends Cast.Listener {
        @Override
        public void onApplicationStatusChanged() {
            super.onApplicationStatusChanged();
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            super.onApplicationDisconnected(statusCode);
        }

        @Override
        public void onVolumeChanged() {
            super.onVolumeChanged();
        }
    }

    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        private boolean waitingForReconnect;

        @Override
        public void onConnected(Bundle connectionHint) {
            if (waitingForReconnect) {
                waitingForReconnect = false;
//                reconnectChannels();
            } else {
                try {
                    Cast.CastApi.launchApplication(apiClient, TDConfig.CAST_APPLICATION_ID, false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result.getApplicationMetadata();
                                                String sessionId = result.getSessionId();
                                                String applicationStatus = result.getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();
                                            } else {
//                                            teardown();
                                            }
                                        }
                                    });

                } catch (Exception e) {
                    Log.e(this.getClass(), e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            waitingForReconnect = true;
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
//            teardown();
        }
    }
}