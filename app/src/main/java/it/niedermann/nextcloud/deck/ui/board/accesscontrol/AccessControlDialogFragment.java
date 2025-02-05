package it.niedermann.nextcloud.deck.ui.board.accesscontrol;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import it.niedermann.nextcloud.deck.DeckLog;
import it.niedermann.nextcloud.deck.R;
import it.niedermann.nextcloud.deck.databinding.DialogBoardShareBinding;
import it.niedermann.nextcloud.deck.model.AccessControl;
import it.niedermann.nextcloud.deck.model.User;
import it.niedermann.nextcloud.deck.model.full.FullBoard;
import it.niedermann.nextcloud.deck.persistence.sync.SyncManager;
import it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.WrappedLiveData;
import it.niedermann.nextcloud.deck.ui.MainViewModel;
import it.niedermann.nextcloud.deck.ui.branding.BrandedActivity;
import it.niedermann.nextcloud.deck.ui.branding.BrandedAlertDialogBuilder;
import it.niedermann.nextcloud.deck.ui.branding.BrandedDialogFragment;
import it.niedermann.nextcloud.deck.ui.card.UserAutoCompleteAdapter;

import static it.niedermann.nextcloud.deck.persistence.sync.adapters.db.util.LiveDataHelper.observeOnce;
import static it.niedermann.nextcloud.deck.ui.board.accesscontrol.AccessControlAdapter.HEADER_ITEM_LOCAL_ID;

public class AccessControlDialogFragment extends BrandedDialogFragment implements AccessControlChangedListener, OnItemClickListener {

    private MainViewModel viewModel;
    private DialogBoardShareBinding binding;

    private static final String KEY_BOARD_ID = "board_id";

    private long boardId;
    private SyncManager syncManager;
    private UserAutoCompleteAdapter userAutoCompleteAdapter;
    private AccessControlAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        final Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_BOARD_ID)) {
            throw new IllegalArgumentException(KEY_BOARD_ID + " must be provided as arguments");
        }

        this.boardId = args.getLong(KEY_BOARD_ID);

        if (this.boardId <= 0L) {
            throw new IllegalArgumentException(KEY_BOARD_ID + " must be a valid local id and not be less or equal 0");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        final AlertDialog.Builder dialogBuilder = new BrandedAlertDialogBuilder(requireContext());

        binding = DialogBoardShareBinding.inflate(requireActivity().getLayoutInflater());
        adapter = new AccessControlAdapter(this, requireContext());
        binding.peopleList.setAdapter(adapter);

        syncManager = new SyncManager(requireActivity());
        syncManager.getFullBoardById(viewModel.getCurrentAccount().getId(), boardId).observe(this, (FullBoard fullBoard) -> {
            if (fullBoard != null) {
                syncManager.getAccessControlByLocalBoardId(viewModel.getCurrentAccount().getId(), boardId).observe(this, (List<AccessControl> accessControlList) -> {
                    final AccessControl ownerControl = new AccessControl();
                    ownerControl.setLocalId(HEADER_ITEM_LOCAL_ID);
                    ownerControl.setUser(fullBoard.getOwner());
                    accessControlList.add(0, ownerControl);
                    adapter.update(accessControlList);
                    userAutoCompleteAdapter = new UserAutoCompleteAdapter(requireActivity(), viewModel.getCurrentAccount(), boardId);
                    binding.people.setAdapter(userAutoCompleteAdapter);
                    binding.people.setOnItemClickListener(this);
                });
            } else {
                // Happens when someone revokes his own access → board gets deleted locally → LiveData fires, but no board
                // see https://github.com/stefan-niedermann/nextcloud-deck/issues/410
                dismiss();
            }
        });
        return dialogBuilder
                .setView(binding.getRoot())
                .setPositiveButton(R.string.simple_close, null)
                .create();
    }

    @Override
    public void updateAccessControl(AccessControl accessControl) {
        syncManager.updateAccessControl(accessControl);
    }

    @Override
    public void deleteAccessControl(AccessControl ac) {
        final WrappedLiveData<Void> wrappedDeleteLiveData = syncManager.deleteAccessControl(ac);
        adapter.remove(ac);
        observeOnce(wrappedDeleteLiveData, this, (ignored) -> {
            if (wrappedDeleteLiveData.hasError()) {
                Toast.makeText(requireContext(), getString(R.string.error_revoking_ac, ac.getUser().getDisplayname()), Toast.LENGTH_LONG).show();
                DeckLog.logError(wrappedDeleteLiveData.getError());
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final AccessControl ac = new AccessControl();
        final User user = userAutoCompleteAdapter.getItem(position);
        ac.setPermissionEdit(true);
        ac.setBoardId(boardId);
        ac.setType(0L); // https://github.com/nextcloud/deck/blob/master/docs/API.md#post-boardsboardidacl---add-new-acl-rule
        ac.setUserId(user.getLocalId());
        ac.setUser(user);
        syncManager.createAccessControl(viewModel.getCurrentAccount().getId(), ac);
        binding.people.setText("");
        userAutoCompleteAdapter.exclude(user);
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        BrandedActivity.applyBrandToEditText(mainColor, textColor, binding.people);
        this.adapter.applyBrand(mainColor, textColor);
    }

    public static DialogFragment newInstance(long boardLocalId) {
        final DialogFragment dialog = new AccessControlDialogFragment();

        final Bundle args = new Bundle();
        args.putLong(KEY_BOARD_ID, boardLocalId);
        dialog.setArguments(args);

        return dialog;
    }
}