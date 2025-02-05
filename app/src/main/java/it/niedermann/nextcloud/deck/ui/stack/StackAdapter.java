package it.niedermann.nextcloud.deck.ui.stack;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.nextcloud.deck.model.full.FullStack;

public class StackAdapter extends FragmentStateAdapter {
    @NonNull
    private List<FullStack> stackList = new ArrayList<>();

    public StackAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @Override
    public int getItemCount() {
        return stackList.size();
    }

    public FullStack getItem(int position) {
        return stackList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return stackList.get(position).getLocalId();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (FullStack stack : stackList) {
            if (stack.getLocalId() == itemId) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return StackFragment.newInstance(stackList.get(position).getLocalId());
    }

    public void setStacks(@NonNull List<FullStack> fullStacks) {
        this.stackList.clear();
        this.stackList.addAll(fullStacks);
        notifyDataSetChanged();
    }
}