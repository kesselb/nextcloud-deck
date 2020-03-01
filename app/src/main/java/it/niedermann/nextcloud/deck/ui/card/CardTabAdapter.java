package it.niedermann.nextcloud.deck.ui.card;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import it.niedermann.nextcloud.deck.R;

public class CardTabAdapter extends FragmentStatePagerAdapter {

    private Context context;
    private long accountId;
    private long localId;
    private long boardId;
    private boolean canEdit;

    public CardTabAdapter(@NonNull FragmentManager fm, @NonNull Context context, long accountId, long localId, long boardId, boolean canEdit) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.context = context;
        this.accountId = accountId;
        this.localId = localId;
        this.boardId = boardId;
        this.canEdit = canEdit;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return CardDetailsFragment.newInstance(accountId, localId, boardId, canEdit);
            case 1:
                return CardAttachmentsFragment.newInstance(accountId, localId, boardId, canEdit);
            case 2:
                return CardActivityFragment.newInstance(accountId, localId, boardId, canEdit);
            default:
                throw new IllegalArgumentException("position " + position + "is not available");
        }
    }

    @NonNull
    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return context.getString(R.string.card_edit_details);
            case 1:
                return context.getString(R.string.card_edit_attachments);
            case 2:
                return context.getString(R.string.card_edit_activity);
            default:
                throw new IllegalArgumentException("position " + position + "is not available");
        }
    }

    @Override
    public int getCount() {
        return 3;
    }
}
