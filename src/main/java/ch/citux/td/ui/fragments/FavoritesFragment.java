package ch.citux.td.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;

import org.apache.commons.lang3.StringUtils;

import ch.citux.td.R;
import ch.citux.td.config.TDConfig;
import ch.citux.td.data.model.Channel;
import ch.citux.td.data.model.Follows;
import ch.citux.td.data.model.Status;
import ch.citux.td.data.worker.TDBasicCallback;
import ch.citux.td.data.worker.TDTaskManager;
import ch.citux.td.ui.adapter.FavoritesAdapter;
import ch.citux.td.ui.widget.EmptyView;

public class FavoritesFragment extends TDFragment<Follows> implements AdapterView.OnItemClickListener {

    private String channelName;
    private SharedPreferences preferences;
    private FavoritesAdapter adapter;

    @Override
    protected int onCreateView() {
        return R.layout.list;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        adapter = new FavoritesAdapter(getActivity());

        setListAdapter(adapter);
        loadData();

        if (!preferences.contains(TDConfig.SETTINGS_CHANNEL_NAME) || preferences.getString(TDConfig.SETTINGS_CHANNEL_NAME, "").equals("")) {
            EmptyView emptyView = (EmptyView) getListView().getEmptyView();
            if (emptyView != null) {
                emptyView.setText(R.string.channel_name_empty);
            }
        }
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void loadData() {
        channelName = preferences.getString(TDConfig.SETTINGS_CHANNEL_NAME, "");
        if (StringUtils.isNotBlank(channelName)) {
            TDTaskManager.getFavorites(this, channelName);
        }
    }

    @Override
    public void refreshData() {
        if (!channelName.equals(preferences.getString(TDConfig.SETTINGS_CHANNEL_NAME, channelName))) {
            //Channel change, reload favorites
            adapter.clear();
            loadData();
        } else {
            //Only status update
            for (Channel channel : adapter.getData()) {
                channel.setStatus(Status.UNKNOWN);
                adapter.updateChannel(channel);
                TDTaskManager.getStatus(new ChannelCallback(this), channel.getName());
            }
        }
    }

    @Override
    public void onResponse(Follows response) {
        if (adapter == null) {
            adapter = new FavoritesAdapter(getActivity(), response.getChannels());
            setListAdapter(adapter);
        } else {
            adapter.setData(response.getChannels());
            getListView().invalidate();
        }
        for (Channel channel : response.getChannels()) {
            TDTaskManager.getStatus(new ChannelCallback(this), channel.getName());
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Channel channel = adapter.getItem(position);
        if (channel != null && channel.getStatus() != null) {
            getTDActivity().showChannel(channel);
        }
    }

    private class ChannelCallback extends TDBasicCallback<Channel> {

        protected ChannelCallback(Object caller) {
            super(caller);
        }

        @Override
        public void onResponse(Channel response) {
            adapter.updateChannel(response);
        }

        @Override
        public boolean isAdded() {
            return FavoritesFragment.this.isAdded();
        }
    }
}