package it.niedermann.nextcloud.deck.ui.stack;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.niedermann.android.crosstabdnd.DragAndDropTab;
import it.niedermann.nextcloud.deck.Application;
import it.niedermann.nextcloud.deck.databinding.FragmentStackBinding;
import it.niedermann.nextcloud.deck.model.Account;
import it.niedermann.nextcloud.deck.model.full.FullCard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.branding.Branded;
import it.niedermann.nextcloud.deck.ui.card.CardAdapter;
import it.niedermann.nextcloud.deck.ui.card.SelectCardListener;

public class StackFragment extends Fragment implements DragAndDropTab<CardAdapter>, Branded {

    private static final String KEY_BOARD_ID = "boardId";
    private static final String KEY_STACK_ID = "stackId";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_HAS_EDIT_PERMISSION = "hasEditPermission";
    private CardAdapter adapter = null;
    private SyncManager syncManager;
    private Activity activity;
    private OnScrollListener onScrollListener;

    private long stackId;
    private long boardId;
    private Account account;
    private boolean canEdit;

    private FragmentStackBinding binding;

    /**
     * @param boardId of the current stack
     * @return new fragment instance
     * @see <a href="https://gunhansancar.com/best-practice-to-instantiate-fragments-with-arguments-in-android/">Best Practice to Instantiate Fragments with Arguments in Android</a>
     */
    public static StackFragment newInstance(long boardId, long stackId, Account account, boolean hasEditPermission) {
        Bundle bundle = new Bundle();
        bundle.putLong(KEY_BOARD_ID, boardId);
        bundle.putLong(KEY_STACK_ID, stackId);
        bundle.putBoolean(KEY_HAS_EDIT_PERMISSION, hasEditPermission);
        bundle.putSerializable(KEY_ACCOUNT, account);

        StackFragment fragment = new StackFragment();
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle == null || !bundle.containsKey(KEY_ACCOUNT) || !bundle.containsKey(KEY_BOARD_ID) || !bundle.containsKey(KEY_STACK_ID)) {
            throw new IllegalArgumentException("account, boardId and localStackId are required arguments.");
        }

        boardId = bundle.getLong(KEY_BOARD_ID);
        stackId = bundle.getLong(KEY_STACK_ID);
        account = (Account) bundle.getSerializable(KEY_ACCOUNT);

        canEdit = bundle.getBoolean(KEY_HAS_EDIT_PERMISSION);

        if (context instanceof OnScrollListener) {
            this.onScrollListener = (OnScrollListener) context;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStackBinding.inflate(inflater, container, false);
        activity = requireActivity();

        syncManager = new SyncManager(activity);

        adapter = new CardAdapter(requireContext(), account, boardId, stackId, canEdit, syncManager, this, (requireActivity() instanceof SelectCardListener) ? (SelectCardListener) requireActivity() : null);
        binding.recyclerView.setAdapter(adapter);

        if (onScrollListener != null) {
            binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0)
                        onScrollListener.onScrollDown();
                    else if (dy < 0)
                        onScrollListener.onScrollUp();
                }
            });
        }

        if (!canEdit) {
            binding.emptyContentView.hideDescription();
        }

        syncManager.getFullCardsForStack(account.getId(), stackId).observe(getViewLifecycleOwner(), (List<FullCard> cards) -> {
            activity.runOnUiThread(() -> {
                if (cards != null && cards.size() > 0) {
                    binding.emptyContentView.setVisibility(View.GONE);
                    adapter.setCardList(cards);
                } else {
                    binding.emptyContentView.setVisibility(View.VISIBLE);
                }
            });
        });
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        Application.registerBrandedComponent(requireContext(), this);
        super.onResume();
    }

    @Override
    public void onPause() {
        Application.deregisterBrandedComponent(this);
        super.onPause();
    }

    @Override
    public CardAdapter getAdapter() {
        return adapter;
    }

    @Override
    public RecyclerView getRecyclerView() {
        return binding.recyclerView;
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        this.adapter.applyBrand(mainColor, textColor);
    }
}