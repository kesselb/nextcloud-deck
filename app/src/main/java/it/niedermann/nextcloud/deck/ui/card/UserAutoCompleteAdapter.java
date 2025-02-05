package it.niedermann.nextcloud.deck.ui.card;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;

import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.ItemAutocompleteUserBinding;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.User;
import it.niedermann.nextcloud.deck.util.AutoCompleteAdapter;
import it.niedermann.nextcloud.deck.util.ViewUtil;

import static it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.LiveDataHelper.observeOnce;

public class UserAutoCompleteAdapter extends AutoCompleteAdapter<User> {
    @NonNull
    private Account account;

    public UserAutoCompleteAdapter(@NonNull ComponentActivity activity, @NonNull Account account, long boardId) {
        this(activity, account, boardId, NO_CARD);
    }

    public UserAutoCompleteAdapter(@NonNull ComponentActivity activity, @NonNull Account account, long boardId, long cardId) {
        super(activity, account.getId(), boardId, cardId);
        this.account = account;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ItemAutocompleteUserBinding binding;

        if (convertView != null) {
            binding = ItemAutocompleteUserBinding.bind(convertView);
        } else {
            binding = ItemAutocompleteUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        }

        ViewUtil.addAvatar(binding.icon, account.getUrl(), getItem(position).getUid(), R.drawable.ic_person_grey600_24dp);
        binding.label.setText(getItem(position).getDisplayname());

        return binding.getRoot();
    }

    @Override
    public Filter getFilter() {
        return new AutoCompleteFilter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                if (constraint != null) {
                    activity.runOnUiThread(() -> {
                        LiveData<List<User>> liveData;
                        final int constraintLength = constraint.toString().trim().length();
                        if (cardId == NO_CARD) {
                            liveData = constraintLength > 0
                                    ? syncManager.searchUserByUidOrDisplayNameForACL(accountId, boardId, constraint.toString())
                                    : syncManager.findProposalsForUsersToAssignForACL(accountId, boardId, activity.getResources().getInteger(R.integer.max_users_suggested));
                        } else {
                            liveData = constraintLength > 0
                                    ? syncManager.searchUserByUidOrDisplayName(accountId, cardId, constraint.toString())
                                    : syncManager.findProposalsForUsersToAssign(accountId, boardId, cardId, activity.getResources().getInteger(R.integer.max_users_suggested));
                        }
                        observeOnce(liveData, activity, users -> {
                            users.removeAll(itemsToExclude);
                            filterResults.values = users;
                            filterResults.count = users.size();
                            publishResults(constraint, filterResults);
                        });
                    });
                }
                return filterResults;
            }
        };
    }
}
