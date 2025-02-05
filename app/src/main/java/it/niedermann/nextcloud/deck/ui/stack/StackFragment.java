package it.niedermann.nextcloud.deck.ui.stack;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.niedermann.android.crosstabdnd.DragAndDropTab;
import it.niedermann.nextcloud.deck.databinding.FragmentStackBinding;
import it.niedermann.nextcloud.deck.model.full.FullCard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.ui.MainViewModel;
import it.niedermann.nextcloud.deck.ui.branding.BrandedFragment;
import it.niedermann.nextcloud.deck.ui.card.CardAdapter;
import it.niedermann.nextcloud.deck.ui.card.SelectCardListener;

public class StackFragment extends BrandedFragment implements DragAndDropTab<CardAdapter> {

    private static final String KEY_STACK_ID = "stackId";

    private FragmentStackBinding binding;
    private SyncManager syncManager;
    private FragmentActivity activity;
    private OnScrollListener onScrollListener;

    private CardAdapter adapter = null;
    private LiveData<List<FullCard>> cardsLiveData;

    private long stackId;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        final Bundle args = getArguments();
        if (args == null || !args.containsKey(KEY_STACK_ID)) {
            throw new IllegalArgumentException(KEY_STACK_ID + " is required.");
        }

        stackId = args.getLong(KEY_STACK_ID);

        if (context instanceof OnScrollListener) {
            this.onScrollListener = (OnScrollListener) context;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStackBinding.inflate(inflater, container, false);
        activity = requireActivity();

        final MainViewModel viewModel = new ViewModelProvider(activity).get(MainViewModel.class);

        syncManager = new SyncManager(activity);

        adapter = new CardAdapter(requireContext(), viewModel.getCurrentAccount(), viewModel.getCurrentBoardLocalId(), stackId, viewModel.currentBoardHasEditPermission(), syncManager, this, (requireActivity() instanceof SelectCardListener) ? (SelectCardListener) requireActivity() : null);
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

        if (!viewModel.currentBoardHasEditPermission()) {
            binding.emptyContentView.hideDescription();
        }

        final Observer<List<FullCard>> cardsObserver = (fullCards) -> activity.runOnUiThread(() -> {
            if (fullCards != null && fullCards.size() > 0) {
                binding.emptyContentView.setVisibility(View.GONE);
                adapter.setCardList(fullCards);
            } else {
                binding.emptyContentView.setVisibility(View.VISIBLE);
            }
        });

        cardsLiveData = syncManager.getFullCardsForStack(viewModel.getCurrentAccount().getId(), stackId, viewModel.getFilterInformation().getValue());
        cardsLiveData.observe(getViewLifecycleOwner(), cardsObserver);

        viewModel.getFilterInformation().observe(getViewLifecycleOwner(), (filterInformation -> {
            cardsLiveData.removeObserver(cardsObserver);
            cardsLiveData = syncManager.getFullCardsForStack(viewModel.getCurrentAccount().getId(), stackId, filterInformation);
            cardsLiveData.observe(getViewLifecycleOwner(), cardsObserver);
        }));

        return binding.getRoot();
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

    public static Fragment newInstance(long stackId) {
        final Bundle args = new Bundle();
        args.putLong(KEY_STACK_ID, stackId);

        final StackFragment fragment = new StackFragment();
        fragment.setArguments(args);

        return fragment;
    }
}